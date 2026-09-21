package com.staydesk.model.request;

import java.math.BigDecimal;

public record ChargeExtraRequest(int reservationId, BigDecimal amount, String description) {
}
