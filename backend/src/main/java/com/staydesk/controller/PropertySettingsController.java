package com.staydesk.controller;

import com.staydesk.model.PropertySetting;
import com.staydesk.service.PropertySettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/settings/property")
public class PropertySettingsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertySettingsController.class);

    private final PropertySettingsService propertySettingsService;

    public PropertySettingsController(PropertySettingsService propertySettingsService) {
        this.propertySettingsService = propertySettingsService;
    }

    @GetMapping
    public List<PropertySetting> getProperties() {
        LOGGER.info("Getting properties");
        return propertySettingsService.getProperties();
    }

    @GetMapping("/{name}")
    public PropertySetting getProperty(@PathVariable String name) {
        LOGGER.info("Getting property {}", name);
        return propertySettingsService.getProperty(name);
    }

    @PutMapping("/{name}")
    public PropertySetting updateProperty(@PathVariable String name, @RequestParam String value) {
        LOGGER.info("Updating property {}", name);
        return propertySettingsService.updateProperty(name, value);
    }
}
