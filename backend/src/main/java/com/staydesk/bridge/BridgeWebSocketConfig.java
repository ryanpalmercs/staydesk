package com.staydesk.bridge;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class BridgeWebSocketConfig implements WebSocketConfigurer {

    private final TerminalBridgeWebSocketHandler handler;
    private final BridgeHandshakeInterceptor handshakeInterceptor;

    public BridgeWebSocketConfig(TerminalBridgeWebSocketHandler handler,
                                 BridgeHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/bridge/terminal")
                .addInterceptors(handshakeInterceptor);
    }
}
