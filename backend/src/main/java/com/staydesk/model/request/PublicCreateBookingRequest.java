package com.staydesk.model.request;

import com.staydesk.model.Rate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * website is a honeypot field - real guests never see or fill it (hidden via CSS on the form), so
 * a non-blank value is a strong bot signal. billingStreet2/specialRequests are the only fully
 * optional fields here; everything else is required for an unauthenticated public booking.
 */
public record PublicCreateBookingRequest(@NotBlank String firstName, @NotBlank String lastName,
                                         @NotBlank @Email String email,
                                         @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "phoneNumber must be exactly 10 digits") String phoneNumber,
                                         boolean smsConsent, @Positive int roomTypeId, @NotNull LocalDate checkInDate,
                                         @NotNull LocalDate checkOutDate, @NotNull Rate.RateType rateType, @Positive int guestCount,
                                         @NotBlank String paymentToken, @Nullable List<Integer> addOnIds,
                                         @Nullable String specialRequests, @NotBlank String billingStreet,
                                         @Nullable String billingStreet2, @NotBlank String billingCity,
                                         @NotBlank String billingState, @NotBlank String billingZip,
                                         @Nullable String website) {
}
