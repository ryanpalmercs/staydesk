package com.staydesk.repository;

import com.staydesk.model.Employee;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends ListCrudRepository<Employee, UUID> {
    Optional<Employee> findByUsername(String username);
}
