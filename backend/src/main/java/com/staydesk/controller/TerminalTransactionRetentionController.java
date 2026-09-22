package com.staydesk.controller;

import com.staydesk.service.TerminalTransactionRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/terminal-transactions/retention")
public class TerminalTransactionRetentionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalTransactionRetentionController.class);

    private final TerminalTransactionRetentionService terminalTransactionRetentionService;

    public TerminalTransactionRetentionController(TerminalTransactionRetentionService terminalTransactionRetentionService) {
        this.terminalTransactionRetentionService = terminalTransactionRetentionService;
    }

    @PostMapping("/run-now")
    public ResponseEntity<String> runNow() {
        LOGGER.info("Manually triggering terminal transaction purge job");
        int purged = terminalTransactionRetentionService.purgeEligibleTransactions();

        if (purged > 0) {
            return ResponseEntity.ok(purged + " terminal transaction(s) purged");
        } else {
            return ResponseEntity.ok("No terminal transactions eligible for purge");
        }
    }
}
