package com.staydesk.service;

import com.staydesk.exception.FolioPaymentNotFoundException;
import com.staydesk.model.Folio;
import com.staydesk.model.FolioPayment;
import com.staydesk.model.FolioPayment.PaymentKind;
import com.staydesk.model.FolioPayment.PaymentStatus;
import com.staydesk.repository.FolioPaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentService.class);

    private final StripeConnectionService stripeConnectionService;
    private final FolioPaymentRepository folioPaymentRepository;
    private final PropertySettingsService propertySettingsService;

    public PaymentService(StripeConnectionService stripeConnectionService,
                          FolioPaymentRepository folioPaymentRepository,
                          PropertySettingsService propertySettingsService) {
        this.stripeConnectionService = stripeConnectionService;
        this.folioPaymentRepository = folioPaymentRepository;
        this.propertySettingsService = propertySettingsService;
    }

    private static long toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private RequestOptions connectedAccountOptions() {
        return RequestOptions.builder()
                             .setStripeAccount(stripeConnectionService.getConnectedAccountId())
                             .build();
    }

    public void createIncidentalHold(Folio folio, String incidentalsPaymentMethodId) {
        LocalDateTime now = LocalDateTime.now();
        RequestOptions options = connectedAccountOptions();

        String holdAmountString = propertySettingsService.getProperty("incidentals_hold_amount").value();
        BigDecimal holdAmount = BigDecimal.ZERO;

        try {
            holdAmount = new BigDecimal(holdAmountString);
        } catch (NumberFormatException e) {
            LOGGER.error("Could not parse hold amount", e);
        }

        createHold(folio.id(), PaymentKind.INCIDENTALS, holdAmount, incidentalsPaymentMethodId, options, now);
    }

    public void createRoomHold(Folio folio, BigDecimal estimatedStayAmount, String roomPaymentMethodId) {
        LocalDateTime now = LocalDateTime.now();
        RequestOptions options = connectedAccountOptions();

        createHold(folio.id(), PaymentKind.ROOM, estimatedStayAmount, roomPaymentMethodId, options, now);
    }

    public void cancelOpenHolds(Folio folio) {
        RequestOptions options = connectedAccountOptions();

        folioPaymentRepository.findByFolioId(folio.id()).stream()
                              .filter(p -> p.status() == PaymentStatus.REQUIRES_CAPTURE)
                              .forEach(p -> cancelHold(p, options));
    }

    private void createHold(int folioId, PaymentKind kind, BigDecimal amount, String paymentMethodId,
                            RequestOptions options, LocalDateTime now) {

        try {
            PaymentIntent intent = PaymentIntent.create(
                    PaymentIntentCreateParams.builder()
                                             .setAmount(toCents(amount))
                                             .setCurrency("usd")
                                             .setPaymentMethod(paymentMethodId)
                                             .addPaymentMethodType("card")
                                             .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                                             .setConfirm(true)
                                             .build(),
                    options
            );

            folioPaymentRepository.save(new FolioPayment(0, folioId, kind, intent.getId(),
                    PaymentStatus.REQUIRES_CAPTURE, amount, null, now, now));
        } catch (StripeException e) {
            throw new RuntimeException("Failed to create " + kind + " hold for folio " + folioId, e);
        }
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

        RequestOptions options = connectedAccountOptions();
        BigDecimal owed = folio.total();

        BigDecimal roomCapture = owed.min(roomHold.authorizedAmount());
        FolioPayment capturedRoom = captureHold(roomHold, roomCapture, options);

        BigDecimal remaining = owed.subtract(roomCapture);
        FolioPayment settledIncidentals;

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal incidentalsCapture = remaining.min(incidentalsHold.authorizedAmount());
            settledIncidentals = captureHold(incidentalsHold, incidentalsCapture, options);
            remaining = remaining.subtract(incidentalsCapture);
        } else {
            settledIncidentals = cancelHold(incidentalsHold, options);
        }

        return new PaymentCaptureResult(capturedRoom, settledIncidentals, remaining);
    }

    private FolioPayment captureHold(FolioPayment hold, BigDecimal amount, RequestOptions options) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(hold.stripePaymentIntentId(), options);
            intent.capture(
                    PaymentIntentCaptureParams.builder()
                                              .setAmountToCapture(toCents(amount))
                                              .build(),
                    options
            );

            return folioPaymentRepository.save(new FolioPayment(hold.id(), hold.folioId(), hold.kind(),
                    hold.stripePaymentIntentId(), PaymentStatus.CAPTURED, hold.authorizedAmount(), amount,
                    hold.createdAt(), LocalDateTime.now()));
        } catch (StripeException e) {
            throw new RuntimeException("Failed to capture " + hold.kind() + " hold " + hold.stripePaymentIntentId(), e);
        }
    }

    private FolioPayment cancelHold(FolioPayment hold, RequestOptions options) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(hold.stripePaymentIntentId(), options);
            intent.cancel(options);

            return folioPaymentRepository.save(new FolioPayment(hold.id(), hold.folioId(), hold.kind(),
                    hold.stripePaymentIntentId(), PaymentStatus.CANCELED, hold.authorizedAmount(), BigDecimal.ZERO,
                    hold.createdAt(), LocalDateTime.now()));
        } catch (StripeException e) {
            throw new RuntimeException("Failed to cancel " + hold.kind() + " hold " + hold.stripePaymentIntentId(), e);
        }
    }

    public void handlePaymentIntentSucceeded(PaymentIntent intent) {
        int updated = folioPaymentRepository.markCaptured(intent.getId(), fromCents(intent.getAmountReceived()));

        if (updated == 0) {
            LOGGER.debug("No FolioPayment updated for PaymentIntent {} (already captured or not found)", intent.getId());
        }
    }

    public record PaymentCaptureResult(FolioPayment room, FolioPayment incidentals, BigDecimal outstandingBalance) {
    }
}