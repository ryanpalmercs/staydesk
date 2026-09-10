package com.staydesk.model.request;

import java.math.BigDecimal;

public record ChargeExtraTerminalRequest(BigDecimal amount, String description, Integer posDeviceId) {
}
