package com.staydesk.model.request;

import com.staydesk.model.Rate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * startDate is the first surged night; endDate is exclusive, the day pricing returns to normal
 * (same convention as Reservation's checkInDate/checkOutDate).
 */
public record CreateRateOverrideRequest(@NotNull Rate.RateType rateType, @Positive int guestCount,
                                        @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                        @NotNull @Positive BigDecimal amount, @NotBlank String label) {
}
