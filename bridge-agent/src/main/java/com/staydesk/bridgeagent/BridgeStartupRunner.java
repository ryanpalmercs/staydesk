package com.staydesk.bridgeagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BridgeStartupRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeStartupRunner.class);

    private final RenderLegClient renderLegClient;
    private final TerminalLegClient terminalLegClient;

    public BridgeStartupRunner(RenderLegClient renderLegClient, TerminalLegClient terminalLegClient) {
        this.renderLegClient = renderLegClient;
        this.terminalLegClient = terminalLegClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LOGGER.info("Connecting to Render at {}", renderLegClient.getURI());
        renderLegClient.connect();

        LOGGER.info("Connecting to terminal at {}", terminalLegClient.getURI());
        terminalLegClient.connect();
    }
}
