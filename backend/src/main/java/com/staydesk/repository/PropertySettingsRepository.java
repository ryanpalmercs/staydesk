package com.staydesk.repository;

import com.staydesk.model.PropertySetting;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface PropertySettingsRepository extends ListCrudRepository<PropertySetting, String> {
    @Modifying
    @Query("UPDATE property_settings SET value = :value WHERE name = :name")
    void updateValue(String name, String value);
}
