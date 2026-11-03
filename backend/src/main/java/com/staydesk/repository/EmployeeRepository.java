package com.staydesk.repository;

import com.staydesk.model.Employee;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends ListCrudRepository<Employee, UUID> {
    Optional<Employee> findByUsername(String username);

    Optional<Employee> findByEmailHash(String emailHash);

    // Names are PII-encrypted at the column level (EncryptedString/PiiCipher), so never ORDER BY
    // a name-like column here - sort by decrypted name in Java after fetching instead.
    @Query("""
            SELECT e.* FROM employees e
            JOIN employee_types et ON et.id = e.employee_type_id
            WHERE et.auth_role = 'HOUSEKEEPING' AND e.active = true
            """)
    List<Employee> findActiveHousekeepingStaff();

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

    @Modifying
    @Query("UPDATE employees SET last_seen_release_notes_id = :releaseNotesId WHERE id = :id")
    void updateLastSeenReleaseNotesId(@Param("id") UUID id, @Param("releaseNotesId") Integer releaseNotesId);
}
