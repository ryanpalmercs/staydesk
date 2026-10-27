package com.staydesk.payroll.quickbooks;

import com.staydesk.exception.PayrollSyncException;
import com.staydesk.model.EncryptedToken;
import com.staydesk.model.QuickBooksConnection;
import com.staydesk.model.QuickBooksStatusResponse;
import com.staydesk.payroll.quickbooks.dto.QuickBooksTokenResponse;
import com.staydesk.repository.QuickBooksConnectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class QuickBooksAuthService {

    private static final int EXPIRY_SAFETY_MARGIN_SECONDS = 30;

    private final RestClient restClient = RestClient.create();
    private final ReentrantLock lock = new ReentrantLock();
    private final QuickBooksConnectionRepository connectionRepository;

    @Value("${quickbooks.token-url}")
    private String tokenUrl;

    @Value("${quickbooks.client-id}")
    private String clientId;

    @Value("${quickbooks.client-secret}")
    private String clientSecret;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.MIN;

    public QuickBooksAuthService(QuickBooksConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    public QuickBooksTokenResponse exchangeCodeForTokens(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        return requestToken(form);
    }

    public void saveConnection(String realmId, QuickBooksTokenResponse tokens) {
        connectionRepository.deleteAll();
        connectionRepository.save(new QuickBooksConnection(0, realmId, new EncryptedToken(tokens.refreshToken()),
                LocalDateTime.now().plusSeconds(tokens.refreshTokenExpiresInSeconds()),
                LocalDateTime.now()
        ));

        cachedToken = tokens.accessToken();
        cachedTokenExpiry = Instant.now().plusSeconds(Math.max(0, tokens.expiresIn() - EXPIRY_SAFETY_MARGIN_SECONDS));
    }

    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }

        lock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
                return cachedToken;
            }

            QuickBooksConnection connection = connectionRepository.findFirst()
                                                                  .orElseThrow(() -> new PayrollSyncException("No QuickBooks account connected"));

            if (connection.refreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
                throw new PayrollSyncException("QuickBooks refresh token has expired - reconnect required");
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", connection.refreshToken().value());
            QuickBooksTokenResponse response = requestToken(form);

            connectionRepository.save(new QuickBooksConnection(
                    connection.id(),
                    connection.realmId(),
                    new EncryptedToken(response.refreshToken()),
                    LocalDateTime.now().plusSeconds(response.refreshTokenExpiresInSeconds()),
                    connection.connectedAt()
            ));

            cachedToken = response.accessToken();
            cachedTokenExpiry = Instant.now().plusSeconds(Math.max(0, response.expiresIn() - EXPIRY_SAFETY_MARGIN_SECONDS));

            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    public String getRealmId() {
        return connectionRepository.findFirst()
                                   .orElseThrow(() -> new PayrollSyncException("No QuickBooks account connected"))
                                   .realmId();
    }

    public QuickBooksStatusResponse getStatus() {
        return connectionRepository.findFirst()
                                   .map(c -> new QuickBooksStatusResponse(true, c.realmId(), c.connectedAt(), c.refreshTokenExpiresAt()))
                                   .orElse(new QuickBooksStatusResponse(false, null, null, null));
    }

    public void disconnect() {
        connectionRepository.deleteAll();
        cachedToken = null;
        cachedTokenExpiry = Instant.MIN;
    }

    private QuickBooksTokenResponse requestToken(MultiValueMap<String, String> formParams) {
        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

        QuickBooksTokenResponse response = restClient.post()
                                                     .uri(tokenUrl)
                                                     .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                                                     .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                     .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                     .body(formParams)
                                                     .retrieve()
                                                     .body(QuickBooksTokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new PayrollSyncException("QuickBooks token request returned no access_token");
        }

        return response;
    }
}
