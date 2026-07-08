package com.staydesk.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateGuestRequest(@NotBlank String firstName, @NotBlank String lastName,
                                 @NotBlank @Email String email,
                                 @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "phoneNumber must be exactly 10 digits") String phoneNumber) {
}
