package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("employee_types")
public record EmployeeType(@Id int id, String name, AuthRole authRole, boolean active) {
}
