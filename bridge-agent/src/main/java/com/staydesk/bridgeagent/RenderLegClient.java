package com.staydesk.bridgeagent;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;

public class RenderLegClient extends WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderLegClient.class);

    private final ReconnectBackoff backoff = new ReconnectBackoff();
    private Consumer<String> onMessage = message -> { };

    public RenderLegClient(URI serverUri, String sharedSecret) {
        super(serverUri, Map.of("X-Bridge-Secret", sharedSecret));
    }

    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.info("Connected to Render at {}", getURI());
        backoff.reset();
    }

    @Override
    public void onMessage(String message) {
        LOGGER.debug("Received from Render: {}", message);
        onMessage.accept(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOGGER.warn("Disconnected from Render (code={}, reason={}) - reconnecting in {}", code, reason, backoff.currentDelay());
        backoff.scheduleReconnect(this::reconnect);
    }

    @Override
    public void onError(Exception ex) {
        LOGGER.error("Render connection error", ex);
    }

    public void relayToRender(String message) {
        if (isOpen()) {
            send(message);
        } else {
            LOGGER.warn("Dropped message to Render - not connected: {}", message);
        }
    }

    public void sendHeartbeat() {
        if (isOpen()) {
            send("{\"heartbeat\": {}}");
        }
    }
}
