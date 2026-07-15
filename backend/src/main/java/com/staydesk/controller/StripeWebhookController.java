package com.staydesk.controller;

import com.staydesk.payment.StripePaymentProvider;
import com.staydesk.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Deprecated
@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentService paymentService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody String payload,
                                              @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            LOGGER.warn("Stripe webhook signature verification failed", e);
            return ResponseEntity.badRequest().build();
        }

        if ("payment_intent.succeeded".equals(event.getType())) {
            event.getDataObjectDeserializer().getObject().ifPresentOrElse(
                    stripeObject -> {
                        PaymentIntent intent = (PaymentIntent) stripeObject;
                        paymentService.confirmCapture(intent.getId(), StripePaymentProvider.fromCents(intent.getAmountReceived()));
                    },
                    () -> LOGGER.warn("payment_intent.succeeded event {} missing deserialized object (API version mismatch?)", event.getId())
            );
        }

        return ResponseEntity.ok().build();
    }
}