package com.staydesk.service;

import com.staydesk.model.Folio;
import com.staydesk.model.FolioPayment;
import com.staydesk.model.ReusablePaymentCredential;
import com.staydesk.payment.PaymentProvider;
import com.staydesk.payment.ReusableCredentialResult;
import com.staydesk.provider.ProviderFactory;
import com.staydesk.repository.ReusablePaymentCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentCredentialService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCredentialService.class);

    private final ProviderFactory providerFactory;
    private final ReusablePaymentCredentialRepository reusablePaymentCredentialRepository;

    public PaymentCredentialService(ProviderFactory providerFactory,
                                    ReusablePaymentCredentialRepository reusablePaymentCredentialRepository) {
        this.providerFactory = providerFactory;
        this.reusablePaymentCredentialRepository = reusablePaymentCredentialRepository;
    }

    public void captureCheckInCredential(Folio folio, String providerName, FolioPayment incidentalsHold) {
        try {
            PaymentProvider provider = providerFactory.getProvider(providerName);
            ReusableCredentialResult result = provider.createReusableCredential(
                    incidentalsHold.stripePaymentIntentId(), "folio-" + folio.id());

            if (!result.success()) {
                LOGGER.error("Could not capture reusable payment credential for folio {}: {}", folio.id(), result.message());
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            reusablePaymentCredentialRepository.save(new ReusablePaymentCredential(0, folio.id(), folio.reservationId(),
                    providerName, result.providerCustomerId(), result.providerToken(), result.cardLast4(),
                    false, null, null, now, now));
        } catch (Exception e) {
            LOGGER.error("Unexpected failure capturing reusable payment credential for folio {}", folio.id(), e);
        }
    }

    public void scheduleExpiry(int folioId, LocalDateTime expiresAt) {
        reusablePaymentCredentialRepository.scheduleExpiry(folioId, expiresAt);
    }
}