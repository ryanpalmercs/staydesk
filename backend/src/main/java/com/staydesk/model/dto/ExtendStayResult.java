package com.staydesk.model.dto;

import com.staydesk.model.Reservation;

import java.math.BigDecimal;

public record ExtendStayResult(Reservation reservation, BigDecimal amountCharged) {
}
