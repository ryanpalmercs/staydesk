package com.staydesk.bridge;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class BridgeStatusController {

    private final TerminalBridgeSessionRegistry sessionRegistry;

    public BridgeStatusController(TerminalBridgeSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/bridge/status")
    public BridgeStatusResponse status() {
        return new BridgeStatusResponse(sessionRegistry.isConnected(), sessionRegistry.lastHeartbeatAt());
    }

    public record BridgeStatusResponse(boolean connected, Instant lastHeartbeatAt) {
    }
}
