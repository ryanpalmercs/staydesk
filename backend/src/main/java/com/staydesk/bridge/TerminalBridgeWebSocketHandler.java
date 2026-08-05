package com.staydesk.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.payment.ingenico.IngenicoBridgeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TerminalBridgeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalBridgeWebSocketHandler.class);

    private final TerminalBridgeSessionRegistry sessionRegistry;
    private final IngenicoBridgeClient bridgeClient;
    private final ObjectMapper objectMapper;

    public TerminalBridgeWebSocketHandler(TerminalBridgeSessionRegistry sessionRegistry,
                                          IngenicoBridgeClient bridgeClient, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.bridgeClient = bridgeClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionRegistry.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root;

        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            LOGGER.warn("Received unparseable message from bridge agent, ignoring: {}", message.getPayload(), e);
            return;
        }

        if (root.has("heartbeat")) {
            sessionRegistry.heartbeat();
            return;
        }

        if (root.has("event")) {
            JsonNode event = root.get("event");
            String flowId = event.path("flow_id").asText(null);

            if (flowId == null) {
                LOGGER.warn("Received event message with no flow_id, ignoring: {}", message.getPayload());
                return;
            }

            bridgeClient.completeFlow(flowId, event.get("resource"));
            return;
        }

        if (root.has("response")) {
            return;
        }

        LOGGER.warn("Received unrecognized message shape from bridge agent: {}", message.getPayload());
    }
}
