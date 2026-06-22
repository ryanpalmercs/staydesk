package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Table("time_entries")
public record TimeEntry(@Id Long id, UUID employeeId, OffsetDateTime clockIn, OffsetDateTime clockOut, LocalDate date,
                        BigDecimal hours, String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
