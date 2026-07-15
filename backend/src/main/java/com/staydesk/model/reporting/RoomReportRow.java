package com.staydesk.model.reporting;

import java.math.BigDecimal;

public record RoomReportRow(int roomId, int roomNumber, int bookedNights, int totalNights, BigDecimal occupancyRate,
                            BigDecimal revenue) {
}
