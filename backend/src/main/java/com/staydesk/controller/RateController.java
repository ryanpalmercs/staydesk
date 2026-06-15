package com.staydesk.controller;

import com.staydesk.model.Rate;
import com.staydesk.repository.RateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/rates")
public class RateController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateController.class);

    private final RateRepository rateRepository;

    public RateController(RateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    @GetMapping
    public List<Rate> getRates() {
        LOGGER.info("Getting all rates");
        return rateRepository.findAll();
    }

    @PutMapping("{id}")
    public ResponseEntity<Rate> updateRate(@PathVariable int id, @RequestBody Rate rate) {
        LOGGER.info("Updating rate with id {}", id);

        if (rateRepository.findById(id).isEmpty()) {
            LOGGER.info("Rate with id {} does not exist", id);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(rateRepository.save(new Rate(id, rate.rateType(), rate.guestCount(), rate.amount(), rate.createdAt(), LocalDateTime.now())));
    }
}
