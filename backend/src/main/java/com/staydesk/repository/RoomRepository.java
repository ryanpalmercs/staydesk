package com.staydesk.repository;

import com.staydesk.model.Room;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RoomRepository extends ListCrudRepository<Room, Integer> {

    @Modifying
    @Query("UPDATE rooms SET status = :status WHERE id = :id")
    void updateRoomStatus(@Param("id") Integer id, @Param("status") Room.RoomStatus status);

    @Query("""
            SELECT * FROM rooms ro
            WHERE ro.room_type_id = :roomTypeId
              AND ro.status != 'MAINTENANCE'
              AND ro.id NOT IN (
                  SELECT r.room_id FROM reservations r
                  WHERE r.room_id IS NOT NULL
                    AND r.check_in_date < :checkOut AND r.check_out_date > :checkIn
                    AND r.status NOT IN ('CANCELLED', 'CHECKED_OUT')
              )
            """)
    List<Room> findAvailableOfType(@Param("roomTypeId") int roomTypeId, @Param("checkOut") LocalDate checkOut, @Param("checkIn") LocalDate checkIn);
}
