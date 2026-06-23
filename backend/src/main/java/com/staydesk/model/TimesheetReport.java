package com.staydesk.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TimesheetReport(LocalDate startDate, LocalDate endDate, List<EmployeeTimesheetRow> employees,
                              BigDecimal totalHours) {

    public record EmployeeTimesheetRow(String name, String payRateDisplay, List<TimeEntry> entries, BigDecimal totalHours) {
    }
}
