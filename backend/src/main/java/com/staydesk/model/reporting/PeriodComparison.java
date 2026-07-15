package com.staydesk.model.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodComparison(LocalDate startDate, LocalDate endDate, BigDecimal totalRevenue, BigDecimal occupancyRate) {
}
