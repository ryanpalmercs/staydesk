package com.staydesk.model.request;

import com.staydesk.model.Extra;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

public record UpdateExtraRequest(@NotBlank String name, @Nullable String description,
                                 @NotNull @Positive BigDecimal price, @NotNull Extra.BillingType billingType,
                                 boolean petFriendlyOnly, boolean active) {
}
