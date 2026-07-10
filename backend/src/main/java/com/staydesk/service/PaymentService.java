package com.staydesk.service;

import com.staydesk.exception.FolioPaymentNotFoundException;
import com.staydesk.model.Folio;
import com.staydesk.model.FolioPayment;
import com.staydesk.model.FolioPayment.PaymentKind;
import com.staydesk.model.FolioPayment.PaymentStatus;
import com.staydesk.payment.AuthResult;
import com.staydesk.payment.CaptureResult;
import com.staydesk.payment.VoidResult;
import com.staydesk.provider.ProviderFactory;
import com.staydesk.repository.FolioPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentService.class);

    private final ProviderFactory providerFactory;
    private final FolioPaymentRepository folioPaymentRepository;
    private final PropertySettingsService propertySettingsService;

    public PaymentService(ProviderFactory providerFactory,
                          FolioPaymentRepository folioPaymentRepository,
                          PropertySettingsService propertySettingsService) {
        this.providerFactory = providerFactory;
        this.folioPaymentRepository = folioPaymentRepository;
        this.propertySettingsService = propertySettingsService;
    }

    public void createIncidentalHold(Folio folio, String incidentalsPaymentMethodId) {
        LocalDateTime now = LocalDateTime.now();

        String holdAmountString = propertySettingsService.getProperty("incidentals_hold_amount").value();
        BigDecimal holdAmount = BigDecimal.ZERO;

        try {
            holdAmount = new BigDecimal(holdAmountString);
        } catch (NumberFormatException e) {
            LOGGER.error("Could not parse hold amount", e);
        }

        createHold(folio.id(), PaymentKind.INCIDENTALS, holdAmount, incidentalsPaymentMethodId, now);
    }

    public void createRoomHold(Folio folio, BigDecimal estimatedStayAmount, String roomPaymentMethodId) {
        createHold(folio.id(), PaymentKind.ROOM, estimatedStayAmount, roomPaymentMethodId, LocalDateTime.now());
    }

    public void cancelOpenHolds(Folio folio) {
        folioPaymentRepository.findByFolioId(folio.id()).stream()
                              .filter(p -> p.status() == PaymentStatus.REQUIRES_CAPTURE)
                              .forEach(this::cancelHold);
    }

    private void createHold(int folioId, PaymentKind kind, BigDecimal amount, String paymentMethodId,
                            LocalDateTime now) {
        AuthResult result = providerFactory.getPaymentProvider()
                                           .authorize(amount, paymentMethodId, kind + " hold for folio " + folioId);

        if (!result.success()) {
            throw new RuntimeException("Failed to create " + kind + " hold for folio " + folioId + ": " + result.message());
        }

        folioPaymentRepository.save(new FolioPayment(0, folioId, kind, result.transactionId(), result.cardLast4(),
                PaymentStatus.REQUIRES_CAPTURE, amount, null, now, now));
    }

    public PaymentCaptureResult capture(Folio folio) {
        List<FolioPayment> payments = folioPaymentRepository.findByFolioId(folio.id());

        FolioPayment roomHold = payments.stream()
                                        .filter(p -> p.kind() == PaymentKind.ROOM)
                                        .findFirst()
                                        .orElseThrow(FolioPaymentNotFoundException::new);

        FolioPayment incidentalsHold = payments.stream()
                                               .filter(p -> p.kind() == PaymentKind.INCIDENTALS)
                                               .findFirst()
                                               .orElseThrow(FolioPaymentNotFoundException::new);

        BigDecimal owed = folio.total();

        BigDecimal roomCapture = owed.min(roomHold.authorizedAmount());
        FolioPayment capturedRoom = captureHold(roomHold, roomCapture);

        BigDecimal remaining = owed.subtract(roomCapture);
        FolioPayment settledIncidentals;

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal incidentalsCapture = remaining.min(incidentalsHold.authorizedAmount());
            settledIncidentals = captureHold(incidentalsHold, incidentalsCapture);
            remaining = remaining.subtract(incidentalsCapture);
        } else {
            settledIncidentals = cancelHold(incidentalsHold);
        }

        return new PaymentCaptureResult(capturedRoom, settledIncidentals, remaining);
    }

    private FolioPayment captureHold(FolioPayment hold, BigDecimal amount) {
        CaptureResult result = providerFactory.getPaymentProvider().capture(hold.stripePaymentIntentId(), amount);

        if (!result.success()) {
            throw new RuntimeException("Failed to capture " + hold.kind() + " hold " + hold.stripePaymentIntentId()
                                       + ": " + result.message());
        }

        return folioPaymentRepository.save(new FolioPayment(hold.id(), hold.folioId(), hold.kind(),
                hold.stripePaymentIntentId(), hold.cardLast4(), PaymentStatus.CAPTURED, hold.authorizedAmount(), amount,
                hold.createdAt(), LocalDateTime.now()));
    }

    private FolioPayment cancelHold(FolioPayment hold) {
        VoidResult result = providerFactory.getPaymentProvider().void_(hold.stripePaymentIntentId());

        if (!result.success()) {
            throw new RuntimeException("Failed to cancel " + hold.kind() + " hold " + hold.stripePaymentIntentId()
                                       + ": " + result.message());
        }

        return folioPaymentRepository.save(new FolioPayment(hold.id(), hold.folioId(), hold.kind(),
                hold.stripePaymentIntentId(), hold.cardLast4(), PaymentStatus.CANCELED, hold.authorizedAmount(),
                BigDecimal.ZERO, hold.createdAt(), LocalDateTime.now()));
    }

    public void confirmCapture(String transactionId, BigDecimal amountReceived) {
        int updated = folioPaymentRepository.markCaptured(transactionId, amountReceived);

        if (updated == 0) {
            LOGGER.debug("No FolioPayment updated for transaction {} (already captured or not found)", transactionId);
        }
    }

    public record PaymentCaptureResult(FolioPayment room, FolioPayment incidentals, BigDecimal outstandingBalance) {
    }
}
