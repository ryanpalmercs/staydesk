package com.staydesk.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenCipher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenCipher.class);
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BYTES = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenCipher(@Value("${sifely.token-encryption-key}") String base64Key) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
        LOGGER.info("Initialized TokenCipher");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BYTES, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            LOGGER.debug("Encrypted a value({} bytes plaintext)", plainText.length());
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array());
        } catch (GeneralSecurityException e) {
            LOGGER.error("Error while encrypting token", e);
            throw new IllegalStateException("Error while encrypting token", e);
        }
    }

    public String decrypt(String storedValue) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(storedValue));
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BYTES, iv));
            String plainText = new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
            LOGGER.debug("Decrypted a value successfully");
            return plainText;
        } catch (GeneralSecurityException e) {
            LOGGER.error("Failed to decrypt value - wrong key or corrupted data", e);
            throw new IllegalStateException("Failed to decrypt value - wrong key or corrupted data", e);
        }
    }
}
