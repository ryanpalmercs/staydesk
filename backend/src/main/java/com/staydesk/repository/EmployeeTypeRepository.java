package com.staydesk.repository;

import com.staydesk.model.EmployeeType;
import org.springframework.data.repository.ListCrudRepository;

public interface EmployeeTypeRepository extends ListCrudRepository<EmployeeType, Integer> {
}
