package com.staydesk.model.reporting;

import java.math.BigDecimal;

public record GuestCountRow(int guestCount, int reservationCount, BigDecimal revenue) {
}
