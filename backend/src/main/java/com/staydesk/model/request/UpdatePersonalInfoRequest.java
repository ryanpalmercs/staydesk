package com.staydesk.model.request;

import com.staydesk.model.ContactInfo;
import com.staydesk.model.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdatePersonalInfoRequest(String firstName, String lastName, BigDecimal payRate, LocalDate hireDate, ContactInfo contactInfo, Employee.PayRateType payRateType) {
}
