package com.staydesk.controller;

import com.staydesk.model.Extra;
import com.staydesk.repository.ExtraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
