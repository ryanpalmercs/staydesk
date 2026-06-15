package com.staydesk.controller;

import com.staydesk.model.StatusResponse;
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
    private final StripeConnectionService stripeConnectionService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public StripeConnectController(StripeConnectionService stripeConnectionService) {
        this.stripeConnectionService = stripeConnectionService;
    }

    @GetMapping("connect")
    public ResponseEntity<Void> connect() {
        LOGGER.info("Connecting Stripe Account");

        try {
            String url = stripeConnectionService.initiateConnect();
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", url).build();
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", frontendUrl + "/settings").build();
        }
    }

    @GetMapping("connect/return")
    public ResponseEntity<Void> connectReturn() {
        LOGGER.info("Stripe Connect onboarding complete");
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", frontendUrl + "/settings").build();
    }

    @GetMapping("connect/refresh")
    public ResponseEntity<Void> connectRefresh() throws StripeException {
        LOGGER.info("Refreshing Stripe Connect onboarding link");
        String url = stripeConnectionService.initiateConnect();
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", url).build();
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
