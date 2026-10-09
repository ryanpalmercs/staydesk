package com.staydesk.model.request;

import jakarta.validation.constraints.NotBlank;

public record RemoteCheckInCompleteRequest(@NotBlank String incidentalsPaymentMethodId, boolean agreedToTerms) {
}
