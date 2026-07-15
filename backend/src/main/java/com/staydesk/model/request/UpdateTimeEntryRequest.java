package com.staydesk.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpdateTimeEntryRequest(@NotNull LocalDate date, @NotNull LocalDateTime clockIn,
                                     @NotNull LocalDateTime clockOut, @NotNull @DecimalMin("0.0") BigDecimal hours,
                                     String notes) {
}
