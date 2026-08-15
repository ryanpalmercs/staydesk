package com.staydesk.payroll.quickbooks;

import com.staydesk.exception.PayrollSyncException;
import com.staydesk.payroll.quickbooks.dto.QuickBooksTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class QuickBooksAuthService {

    private static final int EXPIRY_SAFETY_MARGIN_SECONDS = 30;

    private final RestClient restClient = RestClient.create();
    private final ReentrantLock lock = new ReentrantLock();

    @Value("${quickbooks.token-url}")
    private String tokenUrl;

    @Value("${quickbooks.client-id}")
    private String clientId;

    @Value("${quickbooks.client-secret}")
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

            QuickBooksTokenResponse response = restClient.post()
                                                          .uri(tokenUrl)
                                                          .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                                                          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                          .body("grant_type=client_credentials")
                                                          .retrieve()
                                                          .body(QuickBooksTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new PayrollSyncException("QuickBooks token request returned no access_token");
            }

            cachedToken = response.accessToken();
            cachedTokenExpiry = Instant.now().plusSeconds(Math.max(0, response.expiresIn() - EXPIRY_SAFETY_MARGIN_SECONDS));

            return cachedToken;
        } finally {
            lock.unlock();
        }
    }
}
