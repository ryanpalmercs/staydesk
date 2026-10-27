package com.staydesk.payroll.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuickBooksEmployeeResponse(@JsonProperty("Employee") QuickBooksEmployee employee) {
}
