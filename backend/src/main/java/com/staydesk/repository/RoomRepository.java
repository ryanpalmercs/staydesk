package com.staydesk.repository;

import com.staydesk.model.Room;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends ListCrudRepository<Room, Integer> {

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

    // status is computed live rather than trusted from the stored column: MAINTENANCE is the only
    // value an admin sets directly, OCCUPIED is derived from whether a CHECKED_IN reservation
    // covers "today" (the caller's property-local date, not the DB server's) for this room, and
    // everything else falls back to AVAILABLE. This keeps the two from drifting apart the way a
    // manually-written status column can.
    @Query("""
            SELECT ro.id, ro.room_number, ro.room_type_id,
                   CASE
                       WHEN ro.status = 'MAINTENANCE' THEN 'MAINTENANCE'
                       WHEN EXISTS (
                           SELECT 1 FROM reservations r
                           WHERE r.room_id = ro.id AND r.status = 'CHECKED_IN'
                             AND r.check_in_date <= :today AND r.check_out_date > :today
                       ) THEN 'OCCUPIED'
                       ELSE 'AVAILABLE'
                   END AS status,
                   ro.sifely_lock_id, ro.maintenance_note, ro.created_at, ro.updated_at
            FROM rooms ro
            """)
    List<Room> findAllWithComputedStatus(@Param("today") LocalDate today);

    @Query("""
            SELECT ro.id, ro.room_number, ro.room_type_id,
                   CASE
                       WHEN ro.status = 'MAINTENANCE' THEN 'MAINTENANCE'
                       WHEN EXISTS (
                           SELECT 1 FROM reservations r
                           WHERE r.room_id = ro.id AND r.status = 'CHECKED_IN'
                             AND r.check_in_date <= :today AND r.check_out_date > :today
                       ) THEN 'OCCUPIED'
                       ELSE 'AVAILABLE'
                   END AS status,
                   ro.sifely_lock_id, ro.maintenance_note, ro.created_at, ro.updated_at
            FROM rooms ro
            WHERE ro.id = :id
            """)
    Optional<Room> findByIdWithComputedStatus(@Param("id") Integer id, @Param("today") LocalDate today);
}
