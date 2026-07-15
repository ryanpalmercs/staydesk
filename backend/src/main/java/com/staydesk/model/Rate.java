package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("rates")
public record Rate(@Id int id, String rateType, int guestCount, BigDecimal amount, LocalDateTime createdAt,
                   LocalDateTime updatedAt) {

    public enum RateType {
        NIGHTLY, WEEKLY_5, WEEKLY_7
    }
}
