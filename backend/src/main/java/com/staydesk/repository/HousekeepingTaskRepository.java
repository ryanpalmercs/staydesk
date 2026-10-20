package com.staydesk.repository;

import com.staydesk.model.HousekeepingTask;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HousekeepingTaskRepository extends ListCrudRepository<HousekeepingTask, Integer> {

    @Query("SELECT * FROM housekeeping_tasks WHERE room_id = :roomId ORDER BY created_at DESC LIMIT 1")
    Optional<HousekeepingTask> findCurrentByRoomId(@Param("roomId") int roomId);

    @Query("SELECT DISTINCT ON (room_id) * FROM housekeeping_tasks ORDER BY room_id, created_at DESC")
    List<HousekeepingTask> findAllCurrent();

    @Modifying
    @Query("UPDATE housekeeping_tasks SET assigned_employee_id = :employeeId WHERE id = :id")
    void updateAssignedEmployee(@Param("id") int id, @Param("employeeId") UUID employeeId);

    @Modifying
    @Query("UPDATE housekeeping_tasks SET cleaned_at = :cleanedAt WHERE id = :id")
    void markClean(@Param("id") int id, @Param("cleanedAt") LocalDateTime cleanedAt);

    @Modifying
    @Query("UPDATE housekeeping_tasks SET inspected_at = :inspectedAt WHERE id = :id")
    void markInspected(@Param("id") int id, @Param("inspectedAt") LocalDateTime inspectedAt);
}
