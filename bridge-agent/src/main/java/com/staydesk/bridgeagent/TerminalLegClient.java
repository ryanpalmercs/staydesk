package com.staydesk.bridgeagent;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.function.Consumer;

public class TerminalLegClient extends WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalLegClient.class);

    private final ReconnectBackoff backoff = new ReconnectBackoff();
    private Consumer<String> onMessage = message -> {
    };

    public TerminalLegClient(URI serverUri) {
        super(serverUri);
    }

    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.info("Connected to terminal at {}", getURI());
        backoff.reset();
    }

    @Override
    public void onMessage(String message) {
        LOGGER.debug("Received message from terminal: {}", message);
        onMessage.accept(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOGGER.warn("Disconnected from terminal (code={}, reason={}) - reconnecting in {}", code, reason,
                backoff.currentDelay());
        backoff.scheduleReconnect(this::reconnect);
    }

    @Override
    public void onError(Exception ex) {
        LOGGER.error("Terminal connection error", ex);
    }

    public void relayToTerminal(String message) {
        if (isOpen()) {
            send(message);
        } else {
            LOGGER.warn("Dropped message to terminal - not connected: {}", message);
        }
    }
}
