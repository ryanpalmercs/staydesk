package com.staydesk.repository;

import com.staydesk.model.Employee;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends ListCrudRepository<Employee, UUID> {
    Optional<Employee> findByUsername(String username);

    Optional<Employee> findByEmail(String email);

    @Modifying
    @Query("UPDATE employees SET active = false WHERE id = :id")
    void deactivate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE employees SET employee_type_id = :typeID WHERE id = :id")
    void updateEmployeeType(@Param("id") UUID id, @Param("typeID") int typeID);

    @Modifying
    @Query("UPDATE employees SET first_name = :firstName, last_name = :lastName, pay_rate = :payRate, hire_date = :hireDate, " +
           "contact_info = :contactInfo::jsonb WHERE id = :id")
    void updatePersonalInfo(@Param("id") UUID id, @Param("firstName") String firstName, @Param("lastName") String lastName,
                            @Param("payRate") BigDecimal payRate, @Param("hireDate")LocalDate hireDate, @Param("contactInfo") String contactInfo);
}
