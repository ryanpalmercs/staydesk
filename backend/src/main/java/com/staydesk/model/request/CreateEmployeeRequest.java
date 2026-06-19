package com.staydesk.model.request;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEmployeeRequest(String firstName, String lastName, String email, String username, int employeeTypeId,
                                    BigDecimal payRate, LocalDate hireDate, String pin) {
}
