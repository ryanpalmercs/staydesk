package com.staydesk.model.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportSummaryResponse(LocalDate startDate, LocalDate endDate, BigDecimal totalRevenue, BigDecimal totalTax,
                                    BigDecimal occupancyRate, int occupiedNightCount, int totalRoomNightCount,
                                    BigDecimal averageNightlyRate, List<GuestCountRow> guestCountBreakdown, PeriodComparison comparison,
                                    List<RoomReportRow> roomBreakDown,
                                    /*
                                     * Placeholder terminal-transactions metrics (issue #206) - count of all
                                     * terminal_transactions rows in range and dollar volume of COMPLETED ones,
                                     * plus a per-operation/status breakdown. These are the safe, obvious
                                     * choices and are NOT signed off by the property owner as the final set
                                     * of metrics for this report section - see PR description.
                                     */
                                    int terminalTransactionCount, BigDecimal terminalTransactionVolume,
                                    List<TerminalTransactionReportRow> terminalTransactionBreakdown) {
}
