package com.staydesk.bridgeagent;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatScheduler {

    private static final long HEARTBEAT_INTERVAL_MS = 20_000;

    private final RenderLegClient renderLegClient;

    public HeartbeatScheduler(RenderLegClient renderLegClient) {
        this.renderLegClient = renderLegClient;
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeat() {
        renderLegClient.sendHeartbeat();
    }
}
