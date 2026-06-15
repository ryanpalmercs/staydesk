package com.staydesk.controller;

import com.staydesk.model.StatusResponse;
import com.staydesk.model.StripeConnection;
import com.staydesk.service.StripeConnectionService;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stripe")
public class StripeConnectController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripeConnectController.class);

    @Value("${app.base-url}")
    private String baseUrl;

    private final StripeConnectionService stripeConnectionService;

    public StripeConnectController(StripeConnectionService stripeConnectionService) {
        this.stripeConnectionService = stripeConnectionService;
    }

    @GetMapping("connect")
    public ResponseEntity<Void> connect() {
        LOGGER.info("Connecting Stripe Account");

        String url = stripeConnectionService.buildAuthorizeUrl();

        return ResponseEntity.status(HttpStatus.FOUND).header("Location", url).build();
    }

    @GetMapping("connect/callback")
    public ResponseEntity<Void> connectCallback(@RequestParam String code) {
        LOGGER.info("Connecting Stripe Account via callback");

        try {
            stripeConnectionService.connect(code);
        } catch (StripeException e) {
            LOGGER.error("Failed to connect Stripe Account", e);
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", baseUrl + "/settings?error=stripe_connect_failed").build();
        }

        return ResponseEntity.status(HttpStatus.FOUND).header("Location", baseUrl + "/settings").build();
    }

    @DeleteMapping("connect")
    public ResponseEntity<Void> disconnect() {
        LOGGER.info("Disconnecting Stripe Account");
        try {
            stripeConnectionService.disconnect();
        } catch (StripeException e) {
            LOGGER.error("Failed to disconnect Stripe Account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping("connect/status")
    public ResponseEntity<StatusResponse> getStatus() {
        LOGGER.info("Getting status of Stripe Account");

        return stripeConnectionService.getStatus()
                                      .map(s -> ResponseEntity.ok(new StatusResponse(true, s.stripeAccountId())))
                                      .orElse(ResponseEntity.ok(new StatusResponse(false, null)));
    }
}
