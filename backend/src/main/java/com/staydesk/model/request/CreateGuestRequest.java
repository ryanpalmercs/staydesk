package com.staydesk.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateGuestRequest(@NotBlank String firstName, @NotBlank String lastName,
                                 @Email String email,
                                 @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "phoneNumber must be exactly 10 digits") String phoneNumber,
                                 boolean smsConsent, boolean legacyPricing, BigDecimal legacyPricingAmount) {
}
