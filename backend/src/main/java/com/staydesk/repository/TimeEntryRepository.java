package com.staydesk.repository;

import com.staydesk.model.TimeEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryRepository extends CrudRepository<TimeEntry, Long> {

    @Query("SELECT * FROM time_entries WHERE employee_id = :employeeId AND clock_out IS NULL LIMIT 1")
    Optional<TimeEntry> getOpenEntry(@Param("employeeId") UUID employeeId);

    @Query("SELECT * FROM time_entries WHERE employee_id = :employeeId AND date BETWEEN :start AND :end ORDER BY date, clock_in")
    List<TimeEntry> getByEmployeeAndDateRange(UUID employeeId, LocalDate start, LocalDate end);

    @Query("SELECT * FROM time_entries WHERE date BETWEEN :start AND :end ORDER BY employee_id, date, clock_in")
    List<TimeEntry> getByDateRange(LocalDate start, LocalDate end);
}
