package com.staydesk.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Component
public class BridgeHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeHandshakeInterceptor.class);
    private static final String SECRET_HEADER = "X-Bridge-Secret";

    @Value("${bridge.shared-secret}")
    private String sharedSecret;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String provided = request.getHeaders().getFirst(SECRET_HEADER);

        if (provided == null || !constantTimeEquals(provided, sharedSecret)) {
            LOGGER.warn("Rejected bridge handshake from {} - missing or invalid {} header",
                    request.getRemoteAddress(), SECRET_HEADER);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private boolean constantTimeEquals(String provided, String expected) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
