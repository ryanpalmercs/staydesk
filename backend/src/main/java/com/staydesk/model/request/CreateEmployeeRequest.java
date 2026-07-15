package com.staydesk.model.request;

import com.staydesk.model.ContactInfo;
import com.staydesk.model.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEmployeeRequest(String firstName, String lastName, String email, String username,
                                    int employeeTypeId, BigDecimal payRate, LocalDate hireDate, String pin,
                                    ContactInfo contactInfo, Employee.PayRateType payRateType,
                                    boolean grantDoorAccess) {
}