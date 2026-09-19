package com.staydesk.model.request;

import com.staydesk.model.Guest;
import com.staydesk.model.Rate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UpdateGuestRequest(@NotBlank String firstName, String lastName,
                                 @Email String email,
                                 @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "phoneNumber must be exactly 10 digits") String phoneNumber,
                                 boolean smsConsent, boolean legacyPricing, BigDecimal legacyPricingAmount,
                                 Rate.RateType legacyRateType, boolean regularGuest, @NotNull Guest.GuestType guestType) {
}
