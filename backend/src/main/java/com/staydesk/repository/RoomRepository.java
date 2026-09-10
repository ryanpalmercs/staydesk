package com.staydesk.repository;

import com.staydesk.model.Room;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends ListCrudRepository<Room, Integer> {

    // A room with a currently CHECKED_IN reservation is excluded outright, regardless of what the
    // requested dates are - a guest physically still in the room (even on their own checkout day,
    // before checkOut() has actually run) can't be handed a new guest's key. The date-range
    // exclusion below additionally covers future date conflicts; in practice every reservation that
    // has a room_id also passed through CHECKED_IN at some point, so the two overlap, but the
    // CHECKED_IN check is the one that actually matters here and is kept unconditional on purpose.
    @Query("""
            SELECT * FROM rooms ro
            WHERE ro.room_type_id = :roomTypeId
              AND ro.status != 'MAINTENANCE'
              AND ro.id NOT IN (
                  SELECT r.room_id FROM reservations r
                  WHERE r.room_id IS NOT NULL AND r.status = 'CHECKED_IN'
              )
              AND ro.id NOT IN (
                  SELECT r.room_id FROM reservations r
                  WHERE r.room_id IS NOT NULL
                    AND r.check_in_date < :checkOut AND r.check_out_date > :checkIn
                    AND r.status NOT IN ('CANCELLED', 'CHECKED_OUT')
              )
            """)
    List<Room> findAvailableOfType(@Param("roomTypeId") int roomTypeId, @Param("checkOut") LocalDate checkOut, @Param("checkIn") LocalDate checkIn);

    // status is computed live rather than trusted from the stored column: MAINTENANCE is the only
    // value an admin sets directly, OCCUPIED is derived from whether a reservation for this room
    // currently has status CHECKED_IN - that's already the authoritative "someone is physically in
    // this room right now" signal, so no date-range check belongs here (a guest checking out today
    // is still CHECKED_IN, and still physically in the room, until checkOut() actually runs).
    // Everything else falls back to AVAILABLE. This keeps the two from drifting apart the way a
    // manually-written status column can.
    @Query("""
            SELECT ro.id, ro.room_number, ro.room_type_id,
                   CASE
                       WHEN ro.status = 'MAINTENANCE' THEN 'MAINTENANCE'
                       WHEN EXISTS (
                           SELECT 1 FROM reservations r
                           WHERE r.room_id = ro.id AND r.status = 'CHECKED_IN'
                       ) THEN 'OCCUPIED'
                       ELSE 'AVAILABLE'
                   END AS status,
                   ro.sifely_lock_id, ro.maintenance_note, ro.created_at, ro.updated_at
            FROM rooms ro
            """)
    List<Room> findAllWithComputedStatus();

    @Query("""
            SELECT ro.id, ro.room_number, ro.room_type_id,
                   CASE
                       WHEN ro.status = 'MAINTENANCE' THEN 'MAINTENANCE'
                       WHEN EXISTS (
                           SELECT 1 FROM reservations r
                           WHERE r.room_id = ro.id AND r.status = 'CHECKED_IN'
                       ) THEN 'OCCUPIED'
                       ELSE 'AVAILABLE'
                   END AS status,
                   ro.sifely_lock_id, ro.maintenance_note, ro.created_at, ro.updated_at
            FROM rooms ro
            WHERE ro.id = :id
            """)
    Optional<Room> findByIdWithComputedStatus(@Param("id") Integer id);
}
