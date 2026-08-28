package com.staydesk.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PayrollSyncResponse(LocalDate startDate, LocalDate endDate, int employeesSynced,
                                  BigDecimal totalHoursSynced, List<String> failedEmployees) {
}
