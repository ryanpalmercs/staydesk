package com.staydesk.service;

import com.staydesk.model.PropertySetting;
import com.staydesk.repository.PropertySettingsRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PropertySettingsService {

    private final PropertySettingsRepository propertySettingsRepository;

    public PropertySettingsService(PropertySettingsRepository propertySettingsRepository) {
        this.propertySettingsRepository = propertySettingsRepository;
    }

    public List<PropertySetting> getProperties() {
        return propertySettingsRepository.findAll();
    }

    public PropertySetting getProperty(String name) {
        return propertySettingsRepository.findById(name).orElseThrow();
    }

    public PropertySetting updateProperty(String name, String value) {
        propertySettingsRepository.updateValue(name, value);

        return getProperty(name);
    }
}
