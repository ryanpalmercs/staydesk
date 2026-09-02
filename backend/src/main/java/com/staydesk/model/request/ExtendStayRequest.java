package com.staydesk.model.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ExtendStayRequest(@NotNull LocalDate checkOutDate) {
}
