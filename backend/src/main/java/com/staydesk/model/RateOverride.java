package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("rate_overrides")
public record RateOverride(@Id int id, String rateType, int guestCount, LocalDate startDate,
                           LocalDate endDate, BigDecimal amount, String label, LocalDateTime createdAt,
                           LocalDateTime updatedAt) {
}
