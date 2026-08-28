package com.staydesk.model.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PayrollSyncRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {
}
