package com.staydesk.model.request;

import com.staydesk.model.Rate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BacklogCheckInRequest(@Positive int roomId, @NotBlank String firstName, @NotBlank String lastName,
                                    @Email String email,
                                    @Pattern(regexp = "^[0-9]{10}$", message = "phoneNumber must be exactly 10 digits") String phoneNumber,
                                    @NotNull LocalDate checkInDate, @NotNull LocalDate checkOutDate,
                                    Rate.RateType rateType, Integer guestCount) {
}
