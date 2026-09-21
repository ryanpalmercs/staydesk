package com.staydesk.model.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ExtendStayTerminalRequest(@NotNull LocalDate checkOutDate, Integer posDeviceId) {
}
