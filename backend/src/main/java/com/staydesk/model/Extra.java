package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("extras")
public record Extra(@Id int id, String name, BigDecimal price, boolean active,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
