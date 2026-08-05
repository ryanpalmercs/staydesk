package com.staydesk.bridgeagent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bridge")
public record BridgeAgentProperties(String renderUrl, String sharedSecret, String terminalUrl) {
}
