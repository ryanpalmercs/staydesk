package com.staydesk.model.request;

import java.math.BigDecimal;

public record IncidentChargeRequestBody(BigDecimal amount, String reason) {
}