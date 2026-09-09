package com.staydesk.controller;

import com.staydesk.model.RateOverride;
import com.staydesk.model.request.CreateRateOverrideRequest;
import com.staydesk.service.RateOverrideService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rate-overrides")
public class RateOverrideController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateOverrideController.class);

    private final RateOverrideService rateOverrideService;

    public RateOverrideController(RateOverrideService rateOverrideService) {
        this.rateOverrideService = rateOverrideService;
    }

    @GetMapping
    public List<RateOverride> getRateOverrides() {
        LOGGER.info("Getting all rate overrides");
        return rateOverrideService.getRateOverrides();
    }

    @PostMapping
    public ResponseEntity<RateOverride> createRateOverride(@Valid @RequestBody CreateRateOverrideRequest request) {
        LOGGER.info("Creating rate override for {} guest count {}", request.rateType(), request.guestCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(rateOverrideService.createRateOverride(request));
    }

    @PutMapping("{id}")
    public ResponseEntity<RateOverride> updateRateOverride(@PathVariable int id,
                                                            @Valid @RequestBody CreateRateOverrideRequest request) {
        LOGGER.info("Updating rate override with id {}", id);
        return ResponseEntity.ok(rateOverrideService.updateRateOverride(id, request));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteRateOverride(@PathVariable int id) {
        LOGGER.info("Deleting rate override with id {}", id);
        rateOverrideService.deleteRateOverride(id);
        return ResponseEntity.noContent().build();
    }
}
