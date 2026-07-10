package com.staydesk.model.dto;

import java.math.BigDecimal;

public record ReservationEstimateResponse(BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
}
