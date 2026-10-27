package com.staydesk.controller;

import com.staydesk.exception.ExtraNotFoundException;
import com.staydesk.model.Extra;
import com.staydesk.model.request.CreateExtraRequest;
import com.staydesk.model.request.UpdateExtraRequest;
import com.staydesk.repository.ExtraRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/extras")
public class ExtraController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtraController.class);

    private final ExtraRepository extraRepository;

    public ExtraController(ExtraRepository extraRepository) {
        this.extraRepository = extraRepository;
    }

    @GetMapping
    public List<Extra> getExtras() {
        LOGGER.info("Getting all extras");
        return extraRepository.findAll().stream().filter(Extra::active).toList();
    }

    @GetMapping("/all")
    public List<Extra> getAllExtras() {
        LOGGER.info("Getting all extras, including inactive");
        return extraRepository.findAll();
    }

    @PostMapping
    public Extra createExtra(@Valid @RequestBody CreateExtraRequest request) {
        LOGGER.info("Creating extra {}", request.name());

        LocalDateTime now = LocalDateTime.now();
        return extraRepository.save(new Extra(0, request.name(), request.description(), request.price(),
                true, request.billingType(), request.petFriendlyOnly(), now, now));
    }

    @PutMapping("{id}")
    public Extra updateExtra(@PathVariable int id, @Valid @RequestBody UpdateExtraRequest request) {
        LOGGER.info("Updating extra {}", id);

        Extra existing = extraRepository.findById(id).orElseThrow(ExtraNotFoundException::new);

        Extra updated = new Extra(id, request.name(), request.description(), request.price(), request.active(),
                request.billingType(), request.petFriendlyOnly(), existing.createdAt(), LocalDateTime.now());

        return extraRepository.save(updated);
    }

    @DeleteMapping("{id}")
    public void deleteExtra(@PathVariable int id) {
        LOGGER.info("Deleting extra {}", id);

        extraRepository.findById(id).orElseThrow(ExtraNotFoundException::new);
        extraRepository.deleteById(id);
    }
}
