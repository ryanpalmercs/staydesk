package com.staydesk.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TimesheetReport(LocalDate startDate, LocalDate endDate, List<EmployeeTimesheetRow> employees,
                              BigDecimal totalHours) {

    public record EmployeeTimesheetRow(UUID employeeId, String name, String payRateDisplay, List<TimeEntry> entries,
                                       BigDecimal totalHours) {
    }
}
