package com.staydesk.repository;

import com.staydesk.model.RoomType;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomTypeRepository extends ListCrudRepository<RoomType, Integer> {

    Optional<RoomType> findByName(String name);

    @Modifying
    @Query("UPDATE room_types SET available_count = available_count + 1 WHERE id = :id")
    void incrementAvailableCount(@Param("id") int id);

    @Modifying
    @Query("UPDATE room_types SET available_count = available_count - 1 WHERE id = :id")
    void decrementAvailableCount(@Param("id") int id);

    @Modifying
    @Query("UPDATE room_types SET unavailable_count = unavailable_count + 1 WHERE id = :id")
    void incrementUnavailableCount(@Param("id") int id);

    @Modifying
    @Query("UPDATE room_types SET unavailable_count = unavailable_count - 1 WHERE id = :id")
    void decrementUnavailableCount(@Param("id") int id);
}
