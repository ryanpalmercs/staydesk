package com.staydesk.bridge;

import com.staydesk.exception.TerminalBridgeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TerminalBridgeSessionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalBridgeSessionRegistry.class);

    private final AtomicReference<WebSocketSession> session = new AtomicReference<>();
    private volatile Instant lastHeartbeatAt;

    public synchronized void register(WebSocketSession newSession) {
        WebSocketSession previous = session.getAndSet(newSession);

        if (previous != null && previous.isOpen() && !previous.getId().equals(newSession.getId())) {
            LOGGER.warn("New bridge connection {} replacing still-open connection {} - closing the old one",
                    newSession.getId(), previous.getId());

            try {
                previous.close(CloseStatus.NORMAL.withReason("Replaced by newer bridge connection"));
            } catch (IOException e) {
                LOGGER.warn("Failed to close superseded bridge session {}", previous.getId(), e);
            }
        }

        lastHeartbeatAt = Instant.now();
        LOGGER.info("Bridge agent connected: session {}", newSession.getId());
    }

    public synchronized void unregister(WebSocketSession closedSession) {
        session.compareAndSet(closedSession, null);
        LOGGER.info("Bridge agent disconnected: session {}", closedSession.getId());
    }

    public void heartbeat() {
        lastHeartbeatAt = Instant.now();
    }

    public boolean isConnected() {
        WebSocketSession current = session.get();
        return current != null && current.isOpen();
    }

    public Instant lastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void send(String json) {
        WebSocketSession current = session.get();

        if (current == null || !current.isOpen()) {
            throw new TerminalBridgeException("Terminal bridge is not connected");
        }

        try {
            synchronized (current) {
                current.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            throw new TerminalBridgeException("Failed to send message to terminal bridge", e);
        }
    }
}
