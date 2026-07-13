package com.staydesk.payment.elavon;

import com.staydesk.payment.elavon.dto.CpiTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ElavonCpiAuthService {

    private static final int EXPIRY_SAFETY_MARGIN_SECONDS = 30;

    private final RestClient restClient = RestClient.create();
    private final ReentrantLock lock = new ReentrantLock();

    @Value("${elavon.cpi.base-url}")
    private String baseUrl;

    @Value("${elavon.cpi.client-id}")
    private String clientId;

    @Value("${elavon.cpi.client-secret}")
    private String clientSecret;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.MIN;

    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }

        lock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
                return cachedToken;
            }

            String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

            CpiTokenResponse response = restClient.post()
                                                  .uri(baseUrl + "/credentials/token")
                                                  .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                                                  .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                  .body("grant_type=client_credentials")
                                                  .retrieve()
                                                  .body(CpiTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("Elavon CPI token request returned no access_token");
            }

            cachedToken = response.accessToken();
            cachedTokenExpiry = Instant.now().plusSeconds(Math.max(0, response.expiresIn() - EXPIRY_SAFETY_MARGIN_SECONDS));

            return cachedToken;
        } finally {
            lock.unlock();
        }
    }
}