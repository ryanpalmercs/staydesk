package com.staydesk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.model.dto.SifelyWebhookPayload;
import com.staydesk.service.SifelyWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/sifely")
public class SifelyWebhookController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SifelyWebhookController.class);

    private final SifelyWebhookService sifelyWebhookService;
    private final ObjectMapper objectMapper;

    @Value("${sifely.webhook-secret}")
    private String webhookSecret;

    public SifelyWebhookController(SifelyWebhookService sifelyWebhookService, ObjectMapper objectMapper) {
        this.sifelyWebhookService = sifelyWebhookService;
        this.objectMapper = objectMapper;
    }

    // Sifely's webhook scheme has no signature header (unlike Stripe's Stripe-Signature), so the
    // shared secret is embedded in the URL path we register with Sifely and checked here instead.
    @PostMapping("/{secret}")
    public ResponseEntity<Void> handleWebhook(@PathVariable String secret, @RequestBody String payload) {
        if (!webhookSecret.equals(secret)) {
            LOGGER.warn("Rejected Sifely webhook call with an invalid path secret");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            SifelyWebhookPayload parsed = objectMapper.readValue(payload, SifelyWebhookPayload.class);
            sifelyWebhookService.process(parsed);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Sifely webhook payload: {}", e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
