package com.staydesk.service;

import com.staydesk.exception.FolioPaymentNotFoundException;
import com.staydesk.model.Folio;
import com.staydesk.model.FolioPayment;
import com.staydesk.model.FolioPayment.PaymentKind;
import com.staydesk.model.FolioPayment.PaymentStatus;
import com.staydesk.payment.AuthResult;
import com.staydesk.payment.CaptureResult;
import com.staydesk.payment.PaymentProvider;
import com.staydesk.payment.RefundResult;
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

    public void createIncidentalHold(Folio folio, String providerName, String incidentalsPaymentMethodId) {
        LocalDateTime now = LocalDateTime.now();

        String holdAmountString = propertySettingsService.getProperty("incidentals_hold_amount").value();
        BigDecimal holdAmount = BigDecimal.ZERO;

        try {
            holdAmount = new BigDecimal(holdAmountString);
        } catch (NumberFormatException e) {
            LOGGER.error("Could not parse hold amount", e);
        }

        createHold(folio.id(), PaymentKind.INCIDENTALS, providerName, holdAmount, incidentalsPaymentMethodId, now);
    }

    public void cancelOpenHolds(Folio folio) {
        folioPaymentRepository.findByFolioId(folio.id()).forEach(payment -> {
            if (payment.status() == PaymentStatus.REQUIRES_CAPTURE) {
                cancelHold(payment);
            } else if (payment.status() == PaymentStatus.CAPTURED) {
                refundPayment(payment);
            }
        });
    }

    private FolioPayment refundPayment(FolioPayment payment) {
        PaymentProvider provider = providerFactory.getProvider(payment.provider());

        VoidResult voidResult = provider.void_(payment.stripePaymentIntentId());

        if (!voidResult.success()) {
            RefundResult refundResult = provider.refund(payment.stripePaymentIntentId(), payment.capturedAmount(), payment.cardLast4());

            if (!refundResult.success()) {
                throw new RuntimeException("Failed to void or refund " + payment.kind() + " payment " + payment.stripePaymentIntentId()
                                           + ": " + refundResult.message());
            }
        }

        return folioPaymentRepository.save(new FolioPayment(payment.id(), payment.folioId(), payment.kind(),
                payment.provider(), payment.stripePaymentIntentId(), payment.cardLast4(), PaymentStatus.CANCELED,
                payment.authorizedAmount(), payment.capturedAmount(), payment.createdAt(), LocalDateTime.now()));
    }

    private void createHold(int folioId, PaymentKind kind, String providerName, BigDecimal amount,
                            String paymentMethodId,
                            LocalDateTime now) {
        AuthResult result = providerFactory.getProvider(providerName)
                                           .authorize(amount, paymentMethodId, kind + " hold for folio " + folioId);

        if (!result.success()) {
            throw new RuntimeException("Failed to create " + kind + " hold for folio " + folioId + ": " + result.message());
        }

        folioPaymentRepository.save(new FolioPayment(0, folioId, kind, providerName, result.transactionId(), result.cardLast4(),
                PaymentStatus.REQUIRES_CAPTURE, amount, null, now, now));
    }

    public PaymentCaptureResult capture(Folio folio) {
        List<FolioPayment> payments = folioPaymentRepository.findByFolioId(folio.id());

        FolioPayment roomPayment = payments.stream()
                                           .filter(p -> p.kind() == PaymentKind.ROOM)
                                           .findFirst()
                                           .orElseThrow(FolioPaymentNotFoundException::new);

        FolioPayment incidentalsHold = payments.stream()
                                               .filter(p -> p.kind() == PaymentKind.INCIDENTALS)
                                               .findFirst()
                                               .orElseThrow(FolioPaymentNotFoundException::new);

        BigDecimal owed = folio.total();

        BigDecimal roomAmountCollected;
        FolioPayment capturedRoom;

        if (roomPayment.status() == PaymentStatus.CAPTURED) {
            capturedRoom = roomPayment;
            roomAmountCollected = roomPayment.capturedAmount();
        } else {
            BigDecimal roomCapture = owed.min(roomPayment.authorizedAmount());
            capturedRoom = captureHold(roomPayment, roomCapture);
            roomAmountCollected = roomCapture;
        }

        BigDecimal remaining = owed.subtract(roomAmountCollected);
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
        CaptureResult result = providerFactory.getProvider(hold.provider()).capture(hold.stripePaymentIntentId(), amount);

        if (!result.success()) {
            throw new RuntimeException("Failed to capture " + hold.kind() + " hold " + hold.stripePaymentIntentId()
                                       + ": " + result.message());
        }

        return folioPaymentRepository.save(new FolioPayment(hold.id(), hold.folioId(), hold.kind(),
                hold.provider(), hold.stripePaymentIntentId(), hold.cardLast4(), PaymentStatus.CAPTURED, hold.authorizedAmount(), amount,
                hold.createdAt(), LocalDateTime.now()));
    }

    private FolioPayment cancelHold(FolioPayment hold) {
        VoidResult result = providerFactory.getProvider(hold.provider()).void_(hold.stripePaymentIntentId());

        if (!result.success()) {
            throw new RuntimeException("Failed to cancel " + hold.kind() + " hold " + hold.stripePaymentIntentId()
                                       + ": " + result.message());
        }

        return folioPaymentRepository.save(new FolioPayment(hold.id(), hold.folioId(), hold.kind(),
                hold.provider(), hold.stripePaymentIntentId(), hold.cardLast4(), PaymentStatus.CANCELED, hold.authorizedAmount(),
                BigDecimal.ZERO, hold.createdAt(), LocalDateTime.now()));
    }

    public void confirmCapture(String transactionId, BigDecimal amountReceived) {
        int updated = folioPaymentRepository.markCaptured(transactionId, amountReceived);

        if (updated == 0) {
            LOGGER.debug("No FolioPayment updated for transaction {} (already captured or not found)", transactionId);
        }
    }

    public void chargeFullStay(Folio folio, BigDecimal amount, String providerName, String paymentMethodId) {
        LocalDateTime now = LocalDateTime.now();

        AuthResult result = providerFactory.getProvider(providerName)
                                           .sale(amount, paymentMethodId, "Full stay charge for folio " + folio.id());

        if (!result.success()) {
            throw new RuntimeException("Failed to charge full stay for folio " + folio.id() + ": " + result.message());
        }

        folioPaymentRepository.save(new FolioPayment(0, folio.id(), PaymentKind.ROOM, providerName, result.transactionId(),
                result.cardLast4(), PaymentStatus.CAPTURED, amount, amount, now, now));
    }

    public void refundAllButFirstNight(Folio folio, BigDecimal firstNightAmount) {
        FolioPayment roomPayment = folioPaymentRepository.findByFolioId(folio.id()).stream()
                                                         .filter(p -> p.kind() == PaymentKind.ROOM)
                                                         .filter(p -> p.status() == PaymentStatus.CAPTURED)
                                                         .findFirst()
                                                         .orElseThrow(FolioPaymentNotFoundException::new);

        BigDecimal refundAmount = roomPayment.capturedAmount().subtract(firstNightAmount).max(BigDecimal.ZERO);

        if (refundAmount.compareTo(BigDecimal.ZERO) == 0) {
            LOGGER.info("No refund due for no-show on folio {}: captured {} <= first-night amount {}",
                    folio.id(), roomPayment.capturedAmount(), firstNightAmount);
            return;
        }

        RefundResult result = providerFactory.getProvider(roomPayment.provider())
                                             .refund(roomPayment.stripePaymentIntentId(), refundAmount, roomPayment.cardLast4());

        if (!result.success()) {
            throw new RuntimeException("Failed to refund no-show for folio " + folio.id() + ": " + result.message());
        }

        BigDecimal retainedAmount = roomPayment.capturedAmount().subtract(refundAmount);

        folioPaymentRepository.save(new FolioPayment(roomPayment.id(), roomPayment.folioId(), roomPayment.kind(),
                roomPayment.provider(), roomPayment.stripePaymentIntentId(), roomPayment.cardLast4(), PaymentStatus.PARTIALLY_REFUNDED,
                roomPayment.authorizedAmount(), retainedAmount, roomPayment.createdAt(), LocalDateTime.now()));
    }

    public record PaymentCaptureResult(FolioPayment room, FolioPayment incidentals, BigDecimal outstandingBalance) {
    }
}
