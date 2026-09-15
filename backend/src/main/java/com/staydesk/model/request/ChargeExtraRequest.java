package com.staydesk.model.request;

import java.math.BigDecimal;

public record ChargeExtraRequest(BigDecimal amount, String description) {
}
