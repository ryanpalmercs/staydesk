package com.staydesk.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ManualTimeEntryRequest(@NotNull UUID employeeId, @NotNull LocalDate date, @NotNull OffsetDateTime clockIn, @NotNull OffsetDateTime clockOut,
                                     @NotNull @DecimalMin("0.0") BigDecimal hours, String notes) {
}
