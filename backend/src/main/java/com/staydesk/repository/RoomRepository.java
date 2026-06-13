package com.staydesk.repository;

import com.staydesk.model.Room;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends ListCrudRepository<Room, Integer> {
    @Query("UPDATE room SET status = :status WHERE id = :id")
    void updateRoomStatus(@Param("id") Integer id, @Param("status") Room.RoomStatus status);
}
