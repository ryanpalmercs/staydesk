package com.staydesk.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ManualTimeEntryRequest(@NotNull UUID employeeId, @NotNull LocalDate date, @NotNull LocalDateTime clockIn,
                                     @NotNull LocalDateTime clockOut, @DecimalMin("0.0") BigDecimal hours,
                                     String notes) {
}
