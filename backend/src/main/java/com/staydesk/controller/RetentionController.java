package com.staydesk.controller;

import com.staydesk.service.RetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/retention")
public class RetentionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionController.class);

    private final RetentionService retentionService;

    public RetentionController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @PostMapping("/run-now")
    public ResponseEntity<String> runNow() {
        LOGGER.info("Manually triggering anonymization job");
        int anonymized = retentionService.anonymizeEligibleGuests();

        if (anonymized > 0) {
            return ResponseEntity.ok(anonymized + " guest(s) anonymized");
        } else {
            return ResponseEntity.ok("No guests eligible for anonymization");
        }
    }
}