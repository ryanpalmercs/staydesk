package com.staydesk.payments;

import com.staydesk.config.StripeProperties;
import com.staydesk.folio.FolioAlreadyPaidException;
import com.staydesk.folio.FolioNotFoundException;
import com.staydesk.folio.FolioPaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";

    private final FolioPaymentService folioPaymentService;
    private final StripeProperties stripeProperties;

    public StripeWebhookController(FolioPaymentService folioPaymentService, StripeProperties stripeProperties) {
        this.folioPaymentService = folioPaymentService;
        this.stripeProperties = stripeProperties;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebhookResult> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature
    ) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException | RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebhookResult.rejected("invalid_stripe_signature"));
        }

        if (!PAYMENT_INTENT_SUCCEEDED.equals(event.getType())) {
            return ResponseEntity.ok(WebhookResult.ignored(event.getType()));
        }

        PaymentIntent paymentIntent;
        UUID folioId;
        try {
            paymentIntent = paymentIntentFrom(event);
            folioId = folioIdFrom(paymentIntent.getMetadata());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.unprocessableEntity()
                    .body(WebhookResult.rejected("invalid_payment_intent"));
        }

        try {
            folioPaymentService.markPaidFromStripe(
                    folioId,
                    paymentIntent.getId(),
                    amountReceived(paymentIntent),
                    paidAt(paymentIntent)
            );
        } catch (FolioNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(WebhookResult.rejected("folio_not_found"));
        } catch (FolioAlreadyPaidException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(WebhookResult.rejected("folio_already_paid"));
        }

        return ResponseEntity.ok(WebhookResult.processed(PAYMENT_INTENT_SUCCEEDED));
    }

    private PaymentIntent paymentIntentFrom(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalArgumentException("payment_intent payload could not be deserialized"));
        if (!(stripeObject instanceof PaymentIntent paymentIntent)) {
            throw new IllegalArgumentException("event payload is not a payment intent");
        }
        return paymentIntent;
    }

    private UUID folioIdFrom(Map<String, String> metadata) {
        String rawFolioId = metadata == null ? null : metadata.getOrDefault("folio_id", metadata.get("folioId"));
        if (rawFolioId == null || rawFolioId.isBlank()) {
            throw new IllegalArgumentException("payment intent metadata must include folio_id");
        }
        return UUID.fromString(rawFolioId);
    }

    private long amountReceived(PaymentIntent paymentIntent) {
        Long amountReceived = paymentIntent.getAmountReceived();
        return amountReceived == null ? 0L : amountReceived;
    }

    private Instant paidAt(PaymentIntent paymentIntent) {
        Long created = paymentIntent.getCreated();
        return created == null ? Instant.now() : Instant.ofEpochSecond(created);
    }

    public record WebhookResult(String status, String eventType, String reason) {
        static WebhookResult processed(String eventType) {
            return new WebhookResult("processed", eventType, null);
        }

        static WebhookResult ignored(String eventType) {
            return new WebhookResult("ignored", eventType, null);
        }

        static WebhookResult rejected(String reason) {
            return new WebhookResult("rejected", null, reason);
        }
    }
}
