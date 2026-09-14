package com.staydesk.model.dto;

import java.math.BigDecimal;

public record CheckInEstimateResponse(BigDecimal total, boolean roomChargeDue) {
}
