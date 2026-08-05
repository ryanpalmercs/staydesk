package com.staydesk.bridgeagent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class BridgeClientConfig {

    @Bean
    public TerminalLegClient terminalLegClient(BridgeAgentProperties properties) throws URISyntaxException {
        return new TerminalLegClient(new URI(properties.terminalUrl()));
    }

    @Bean
    public RenderLegClient renderLegClient(BridgeAgentProperties properties, TerminalLegClient terminalLegClient) throws URISyntaxException {
        RenderLegClient renderLegClient = new RenderLegClient(new URI(properties.renderUrl()), properties.sharedSecret());

        terminalLegClient.setOnMessage(renderLegClient::relayToRender);
        renderLegClient.setOnMessage(terminalLegClient::relayToTerminal);

        return renderLegClient;
    }
}
