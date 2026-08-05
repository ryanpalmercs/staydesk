package com.staydesk.bridgeagent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class ReconnectBackoff {

    private static final long INITIAL_DELAY_SECONDS = 5;
    private static final long MAX_DELAY_SECONDS = 60;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private long currentDelaySeconds = INITIAL_DELAY_SECONDS;

    void reset() {
        currentDelaySeconds = INITIAL_DELAY_SECONDS;
    }

    String currentDelay() {
        return currentDelaySeconds + "s";
    }

    void scheduleReconnect(Runnable reconnectAction) {
        scheduler.schedule(reconnectAction, currentDelaySeconds, TimeUnit.SECONDS);
        currentDelaySeconds = Math.min(currentDelaySeconds * 2, MAX_DELAY_SECONDS);
    }
}
