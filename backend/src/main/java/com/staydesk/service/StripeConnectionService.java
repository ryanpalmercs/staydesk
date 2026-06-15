package com.staydesk.service;

import com.staydesk.model.StripeConnection;
import com.staydesk.repository.StripeConnectionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.oauth.TokenResponse;
import com.stripe.net.OAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
public class StripeConnectionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripeConnectionService.class);

    private final StripeConnectionRepository stripeConnectionRepository;

    @Value("${stripe.client-id")
    private String clientId;

    @Value("${stripe.redirect-url")
    private String redirectUrl;

    public StripeConnectionService(StripeConnectionRepository stripeConnectionRepository) {
        this.stripeConnectionRepository = stripeConnectionRepository;
    }

    private String getConnectedAccountId() {
        return getStatus()
                .map(StripeConnection::stripeAccountId)
                .orElseThrow(() -> new IllegalStateException("No Stripe account connected"));
    }

    public String buildAuthorizeUrl() {
        return String.format("https://connect.stripe.com/oauth/authorize?response_type=code&client_id=%s&scope=read_write&redirect_uri=%s", clientId, redirectUrl);
    }

    public Optional<StripeConnection> getStatus() {
        return StreamSupport.stream(stripeConnectionRepository.findAll().spliterator(), false).findFirst();
    }

    public void connect(String code) throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("code", code);
        params.put("grant_type", "authorization_code");

        TokenResponse response = OAuth.token(params, null);

        stripeConnectionRepository.deleteAll();
        stripeConnectionRepository.save(new StripeConnection(0, response.getStripeUserId(), LocalDateTime.now()));
    }

    public void disconnect() throws StripeException {
        Optional<StripeConnection> existing = getStatus();

        if (existing.isEmpty()) {
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("stripe_user_id", existing.get().stripeAccountId());
        OAuth.deauthorize(params, null);

        stripeConnectionRepository.deleteAll();
    }
}
