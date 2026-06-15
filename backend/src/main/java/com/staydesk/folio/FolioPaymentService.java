package com.staydesk.folio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class FolioPaymentService {

    private final FolioRepository folioRepository;

    public FolioPaymentService(FolioRepository folioRepository) {
        this.folioRepository = folioRepository;
    }

    @Transactional
    public Folio markPaidFromStripe(UUID folioId, String paymentIntentId, long amountReceivedCents, Instant paidAt) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("Stripe payment intent id is required");
        }
        if (amountReceivedCents < 0) {
            throw new IllegalArgumentException("Stripe amount received cannot be negative");
        }

        Folio folio = folioRepository.findById(folioId)
                .orElseThrow(() -> new FolioNotFoundException(folioId));

        if (folio.isPaid()) {
            if (Objects.equals(folio.getStripePaymentIntentId(), paymentIntentId)) {
                return folio;
            }
            throw new FolioAlreadyPaidException(folioId);
        }

        folio.markPaid(paymentIntentId, amountReceivedCents, paidAt);
        return folioRepository.save(folio);
    }
}
