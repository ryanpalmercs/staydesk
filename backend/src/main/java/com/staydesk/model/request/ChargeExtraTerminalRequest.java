package com.staydesk.model.request;

import java.math.BigDecimal;

public record ChargeExtraTerminalRequest(int reservationId, BigDecimal amount, String description, Integer posDeviceId) {
}
