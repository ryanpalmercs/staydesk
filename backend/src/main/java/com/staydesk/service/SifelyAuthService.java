package com.staydesk.service;

import com.staydesk.model.EncryptedToken;
import com.staydesk.model.SifelyConnection;
import com.staydesk.model.dto.SifelyLoginResponse;
import com.staydesk.repository.SifelyConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class SifelyAuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SifelyAuthService.class);

    private final RestClient restClient = RestClient.create();
    private final SifelyConnectionRepository sifelyConnectionRepository;

    @Value("${sifely.base-url}")
    private String baseUrl;

    public SifelyAuthService(SifelyConnectionRepository sifelyConnectionRepository) {
        this.sifelyConnectionRepository = sifelyConnectionRepository;
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password", e);
        }
    }

    public void connect(String account, String password) {
        LOGGER.info("Connecting Sifely account for {}", account);

        SifelyLoginResponse response = restClient.post()
                                                 .uri(baseUrl + "/system/smart/login")
                                                 .contentType(APPLICATION_JSON)
                                                 .body(Map.of("account", account, "password", md5Hex(password)))
                                                 .retrieve()
                                                 .body(SifelyLoginResponse.class);

        if (response == null || response.data() == null || response.data().clientToken() == null) {
            LOGGER.error("Sifely login failed, no client token returned");
            throw new IllegalStateException("Sifely account could not be connected, no token returned");
        }

        sifelyConnectionRepository.deleteAll();
        sifelyConnectionRepository.save(new SifelyConnection(
                0,
                new EncryptedToken(response.data().clientToken()),
                response.data().clientId(),
                LocalDateTime.now()
        ));

        LOGGER.info("Sifely account connected successfully");
    }

    public String getApiKey() {
        return sifelyConnectionRepository.findFirst()
                                         .orElseThrow(() -> new IllegalStateException("No Sifely account connected"))
                                         .clientToken()
                                         .value();
    }
}