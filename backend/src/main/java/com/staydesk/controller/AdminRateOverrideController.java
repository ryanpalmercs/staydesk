package com.staydesk.controller;

import com.staydesk.service.RateOverrideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rate-overrides")
public class AdminRateOverrideController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminRateOverrideController.class);

    private final RateOverrideService rateOverrideService;

    public AdminRateOverrideController(RateOverrideService rateOverrideService) {
        this.rateOverrideService = rateOverrideService;
    }

    @PostMapping("/reconcile-all")
    public ResponseEntity<String> reconcileAll() {
        LOGGER.info("Manually triggering reconciliation for all active rate overrides");
        int checked = rateOverrideService.reconcileAllActiveOverrides();
        return ResponseEntity.ok(checked + " reservation(s) checked against active rate overrides");
    }
}
