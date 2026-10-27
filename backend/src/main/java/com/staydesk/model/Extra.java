package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("extras")
public record Extra(@Id int id, String name, @Nullable String description, BigDecimal price, boolean active,
                    BillingType billingType, boolean petFriendlyOnly, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum BillingType {
        FLAT, PER_NIGHT
    }
}
