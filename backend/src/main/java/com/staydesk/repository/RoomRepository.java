package com.staydesk.repository;

import com.staydesk.model.Room;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends ListCrudRepository<Room, Integer> {

    // A room is excluded when an active reservation on it overlaps the requested dates - same-day
    // turnover is allowed, so a room becomes available starting the day its current occupant checks
    // out, even if that checkout hasn't actually been run yet. The one exception is a CHECKED_IN
    // regular/long-term guest: their actual departure date is unknown (a renewal takes priority over
    // a new booking that hasn't happened yet), so they block the room indefinitely regardless of the
    // requested dates - same carve-out as the room-type-level availability math.
    // excludeReservationId lets a reservation that already holds this room (assigned ahead of
    // check-in via assignRoom) see that same room as available to itself - without it, the room's
    // own overlapping reservation row would make it look occupied by itself.
    @Query("""
            SELECT * FROM rooms ro
            WHERE ro.room_type_id = :roomTypeId
              AND ro.status != 'MAINTENANCE'
              AND ro.id NOT IN (
                  SELECT r.room_id FROM reservations r LEFT JOIN guests g ON g.id = r.guest_id
                  WHERE r.room_id IS NOT NULL
                    AND r.status NOT IN ('CANCELLED', 'CHECKED_OUT', 'NO_SHOW')
                    AND r.check_in_date < :checkOut
                    AND (r.check_out_date > :checkIn OR (r.status = 'CHECKED_IN' AND COALESCE(g.regular_guest, false)))
                    AND (:excludeReservationId::int IS NULL OR r.id != :excludeReservationId::int)
              )
            """)
    List<Room> findAvailableOfType(@Param("roomTypeId") int roomTypeId, @Param("checkOut") LocalDate checkOut,
                                   @Param("checkIn") LocalDate checkIn,
                                   @Param("excludeReservationId") Integer excludeReservationId);

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
