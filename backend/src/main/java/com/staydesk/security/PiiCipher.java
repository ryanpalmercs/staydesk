package com.staydesk.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class PiiCipher {
    private static final Logger LOGGER = LoggerFactory.getLogger(PiiCipher.class);
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BYTES = 128;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hashKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiCipher(@Value("${guest.pii-encryption-key}") String base64EncryptionKey,
                     @Value("${guest.pii-hash-key}") String base64HashKey) {
        this.encryptionKey = new SecretKeySpec(Base64.getDecoder().decode(base64EncryptionKey), "AES");
        this.hashKey = new SecretKeySpec(Base64.getDecoder().decode(base64HashKey), HMAC_ALGORITHM);
        LOGGER.info("Initialized PiiCipher");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BYTES, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array());
        } catch (GeneralSecurityException e) {
            LOGGER.error("Error while encrypting PII value", e);
            throw new IllegalStateException("Error while encrypting PII value", e);
        }
    }

    public String decrypt(String storedValue) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(storedValue));
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BYTES, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            LOGGER.error("Failed to decrypt PII value - wrong key or corrupted data", e);
            throw new IllegalStateException("Failed to decrypt PII value - wrong key or corrupted data", e);
        }
    }

    /**
     * Deterministic HMAC-SHA256 blind index for exact-match lookups on encrypted columns
     * (e.g. email uniqueness/lookup). Not reversible.
     */
    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hashKey);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            LOGGER.error("Error while hashing PII value", e);
            throw new IllegalStateException("Error while hashing PII value", e);
        }
    }
}
