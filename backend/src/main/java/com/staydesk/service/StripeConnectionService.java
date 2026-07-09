package com.staydesk.service;

import com.staydesk.model.StripeConnection;
import com.staydesk.repository.StripeConnectionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
public class StripeConnectionService {
    private final StripeConnectionRepository stripeConnectionRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public StripeConnectionService(StripeConnectionRepository stripeConnectionRepository) {
        this.stripeConnectionRepository = stripeConnectionRepository;
    }

    public String getConnectedAccountId() {
        return getStatus()
                .map(StripeConnection::stripeAccountId)
                .orElseThrow(() -> new IllegalStateException("No Stripe account connected"));
    }

    public Optional<StripeConnection> getStatus() {
        return StreamSupport.stream(stripeConnectionRepository.findAll().spliterator(), false).findFirst();
    }

    public String initiateConnect() throws StripeException {
        String accountId = getStatus()
                .map(StripeConnection::stripeAccountId)
                .orElseGet(() -> {
                    try {
                        Account account = Account.create(
                                AccountCreateParams.builder()
                                                   .setType(AccountCreateParams.Type.STANDARD)
                                                   .build()
                        );
                        stripeConnectionRepository.deleteAll();
                        stripeConnectionRepository.save(new StripeConnection(0, account.getId(), LocalDateTime.now()));
                        return account.getId();
                    } catch (StripeException e) {
                        throw new RuntimeException(e);
                    }
                });

        AccountLink link = AccountLink.create(
                AccountLinkCreateParams.builder()
                                       .setAccount(accountId)
                                       .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                                       .setReturnUrl(baseUrl + "/stripe/connect/return")
                                       .setRefreshUrl(baseUrl + "/stripe/connect/refresh")
                                       .build()
        );

        return link.getUrl();
    }

    public void disconnect() throws StripeException {
        Optional<StripeConnection> existing = getStatus();

        if (existing.isEmpty()) {
            return;
        }

        stripeConnectionRepository.deleteAll();
    }
}
