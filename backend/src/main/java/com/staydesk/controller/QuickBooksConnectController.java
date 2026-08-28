package com.staydesk.controller;

import com.staydesk.model.QuickBooksAuthorizeUrlResponse;
import com.staydesk.model.QuickBooksStatusResponse;
import com.staydesk.payroll.quickbooks.QuickBooksAuthService;
import com.staydesk.payroll.quickbooks.dto.QuickBooksTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@RestController
public class QuickBooksConnectController {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuickBooksConnectController.class);
    private static final int STALE_TTL_SECONDS = 600;

    private final QuickBooksAuthService quickBooksAuthService;
    private final SecureRandom secureRandom = new SecureRandom();

    private volatile String pendingState;
    private volatile Instant pendingStateExpiry = Instant.MIN;

    @Value("${quickbooks.authorize-url}")
    private String authorizeUrl;

    @Value("${quickbooks.client-id}")
    private String clientId;

    @Value("${quickbooks.redirect-uri}")
    private String redirectUri;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public QuickBooksConnectController(QuickBooksAuthService quickBooksAuthService) {
        this.quickBooksAuthService = quickBooksAuthService;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @GetMapping("/admin/quickbooks/connect")
    public ResponseEntity<QuickBooksAuthorizeUrlResponse> connect() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        pendingState = Base64.getEncoder().withoutPadding().encodeToString(bytes);
        pendingStateExpiry = Instant.now().plusSeconds(STALE_TTL_SECONDS);

        String url = authorizeUrl
                     + "?client_id=" + encode(clientId)
                     + "&redirect_uri=" + encode(redirectUri)
                     + "&response_type=code"
                     + "&scope=" + encode("com.intuit.quickbooks.accounting")
                     + "&state=" + encode(pendingState);

        return ResponseEntity.ok(new QuickBooksAuthorizeUrlResponse(url));
    }

    @GetMapping("/quickbooks/connect/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code, @RequestParam("realmId") String realmId,
                                         @RequestParam("state") String state) {
        boolean stateValid = state != null && state.equals(pendingState) && Instant.now().isBefore(pendingStateExpiry);
        pendingState = null;
        pendingStateExpiry = Instant.MIN;

        if (!stateValid) {
            LOGGER.warn("QuicBooks OAuth callback rejected - state mismatch or expired");
            return redirectToFrontend("error");
        }

        try {
            QuickBooksTokenResponse tokens = quickBooksAuthService.exchangeCodeForTokens(code, redirectUri);
            quickBooksAuthService.saveConnection(realmId, tokens);
            LOGGER.info("QuickBooks account connected (realmId={})", realmId);
            return redirectToFrontend("connected");
        } catch (Exception e) {
            LOGGER.error("QuickBooks OAuth callback failed: {}", e.getMessage());
            return redirectToFrontend("error");
        }
    }

    @GetMapping("/admin/quickbooks/status")
    public ResponseEntity<QuickBooksStatusResponse> status() {
        return ResponseEntity.ok(quickBooksAuthService.getStatus());
    }

    @DeleteMapping("/admin/quickbooks/connect")
    public ResponseEntity<Void> disconnect() {
        quickBooksAuthService.disconnect();
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> redirectToFrontend(String result) {
        return ResponseEntity.status(HttpStatus.FOUND)
                             .header(HttpHeaders.LOCATION, frontendUrl + "/settings?quickbooks=" + result)
                             .build();

    }
}

