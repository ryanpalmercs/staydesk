package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("employees")
public record Employee(@Id UUID id, String firstName, String lastName, String email, String username, int employeeTypeId,
                       BigDecimal payRate, LocalDate hireDate, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public String getName() {
        return firstName + " " + lastName;
    }
}
