package com.staydesk.repository;

import com.staydesk.model.RoomType;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends ListCrudRepository<RoomType, Integer> {

    Optional<RoomType> findByName(String name);

    @Query("SELECT rt.* FROM room_types rt WHERE EXISTS (SELECT 1 FROM rooms r WHERE r.room_type_id = rt.id)")
    List<RoomType> findAllWithAtLeastOneRoom();

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
