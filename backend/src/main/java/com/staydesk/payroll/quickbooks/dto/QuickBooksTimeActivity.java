package com.staydesk.payroll.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QuickBooksTimeActivity(@JsonProperty("NameOf") String nameOf, @JsonProperty("EmployeeRef") EmployeeRef employeeRef,
                                     @JsonProperty("TxnDate") LocalDate txnDate, @JsonProperty("Hours") int hours,
                                     @JsonProperty("Minutes") int minutes, @JsonProperty("Description") String description) {

    // QuickBooks' TimeActivity entity is inherently per-day; we post one row per pay period
    // (dated at the period end) carrying the period's total hours, rather than one row per shift.
    public static QuickBooksTimeActivity forPeriod(String quickBooksEmployeeId, LocalDate periodEndDate, BigDecimal totalHours,
                                                    String description) {
        int wholeHours = totalHours.intValue();
        int minutes = totalHours.subtract(BigDecimal.valueOf(wholeHours)).multiply(BigDecimal.valueOf(60)).intValue();

        return new QuickBooksTimeActivity("Employee", new EmployeeRef(quickBooksEmployeeId), periodEndDate, wholeHours, minutes,
                description);
    }

    public record EmployeeRef(@JsonProperty("value") String value) {
    }
}
