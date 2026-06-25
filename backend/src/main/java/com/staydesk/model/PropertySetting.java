package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("property_settings")
public record PropertySetting(@Id String name, String value, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
