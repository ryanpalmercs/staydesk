package com.staydesk.model.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportSummaryResponse(LocalDate startDate, LocalDate endDate, BigDecimal totalRevenue, BigDecimal totalTax,
                                    BigDecimal occupancyRate, int occupiedNightCount, int totalRoomNightCount,
                                    BigDecimal averageNightlyRate, List<GuestCountRow> guestCountBreakdown, PeriodComparison comparison) {
}
