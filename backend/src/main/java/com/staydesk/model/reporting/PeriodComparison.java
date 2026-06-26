package com.staydesk.model.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodComparison(LocalDate start, LocalDate end, BigDecimal totalRevenue, BigDecimal occupancyRate) {
}
