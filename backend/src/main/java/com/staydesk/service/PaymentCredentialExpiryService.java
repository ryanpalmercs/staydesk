package com.staydesk.service;

import com.staydesk.model.ReusablePaymentCredential;
import com.staydesk.provider.ProviderFactory;
import com.staydesk.repository.ReusablePaymentCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentCredentialExpiryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCredentialExpiryService.class);

    private final ReusablePaymentCredentialRepository reusablePaymentCredentialRepository;
    private final ProviderFactory providerFactory;

    public PaymentCredentialExpiryService(ReusablePaymentCredentialRepository reusablePaymentCredentialRepository,
                                          ProviderFactory providerFactory) {
        this.reusablePaymentCredentialRepository = reusablePaymentCredentialRepository;
        this.providerFactory = providerFactory;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void expireStaleCredentials() {
        reusablePaymentCredentialRepository.findByExpiresAtBeforeAndRevokedFalse(LocalDateTime.now())
                                           .forEach(this::revoke);
    }

    private void revoke(ReusablePaymentCredential credential) {
        try {
            providerFactory.getProvider(credential.provider())
                           .revokeReusableCredential(credential.providerCustomerId(), credential.providerToken());
        } catch (Exception e) {
            LOGGER.warn("Remote revocation failed for credential {} (provider {}); marking revoked locally regardless",
                    credential.id(), credential.provider(), e);
        }

        reusablePaymentCredentialRepository.markRevoked(credential.id(), LocalDateTime.now());
    }
}