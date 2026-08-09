package com.staydesk.repository;

import com.staydesk.model.Employee;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends ListCrudRepository<Employee, UUID> {
    Optional<Employee> findByUsername(String username);

    Optional<Employee> findByEmailHash(String emailHash);

    @Modifying
    @Query("UPDATE employees SET active = false WHERE id = :id")
    void deactivate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE employees SET employee_type_id = :typeID WHERE id = :id")
    void updateEmployeeType(@Param("id") UUID id, @Param("typeID") int typeID);

    @Modifying
    @Query("UPDATE employees SET door_access_enabled = :enabled WHERE id = :id")
    void updateDoorAccessEnabled(@Param("id") UUID id, @Param("enabled") boolean enabled);

    @Modifying
    @Query("UPDATE employees SET active = true WHERE id = :id")
    void activate(@Param("id") UUID id);
}
