package com.staydesk.repository;

import com.staydesk.model.Reservation;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends ListCrudRepository<Reservation, Integer> {

    @Query("SELECT * FROM reservations WHERE room_id = :roomId AND check_in_date < :checkOut AND check_out_date > :checkIn AND status NOT IN ('CANCELLED', 'CHECKED_OUT', 'NO_SHOW')")
    List<Reservation> findOverlapping(@Param("roomId") int roomId, @Param("checkOut") LocalDate checkOut,
                                      @Param("checkIn") LocalDate checkIn);

    @Query("SELECT * FROM reservations WHERE room_id = :roomId AND status NOT IN ('CANCELLED', 'CHECKED_OUT', 'NO_SHOW') ORDER BY check_in_date")
    List<Reservation> findActiveByRoomId(@Param("roomId") int roomId);

    @Query("SELECT * FROM reservations WHERE status = 'CONFIRMED' AND channel = 'PHONE' AND check_in_date < :date")
    List<Reservation> findNoShowCandidates(@Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE reservations SET room_id = :roomId WHERE id = :id")
    void assignRoom(@Param("id") Integer id, @Param("roomId") Integer roomId);

    // Also updates room_type_id, not just room_id - moving a CHECKED_IN guest to a room of a
    // different type has to keep the reservation's own room type in sync, since room-type
    // capacity/occupancy math (RoomTypeAvailabilityRepository et al.) keys off it, not room_id.
    @Modifying
    @Query("UPDATE reservations SET room_id = :roomId, room_type_id = :roomTypeId WHERE id = :id")
    void moveRoom(@Param("id") Integer id, @Param("roomId") Integer roomId, @Param("roomTypeId") Integer roomTypeId);

    @Modifying
    @Query("UPDATE reservations SET status = 'CHECKED_IN', checked_in_at = now() WHERE id = :id")
    void updateReservationStatusToCheckedIn(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE reservations SET status = 'CHECKED_OUT', checked_out_at = now() WHERE id = :id")
    void updateReservationStatusToCheckedOut(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE reservations SET legal_hold = TRUE WHERE id = :id")
    void setLegalHold(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE reservations SET legal_hold = FALSE WHERE id = :id")
    void clearLegalHold(@Param("id") Integer id);

    List<Reservation> findByGuestId(Integer guestId);

    boolean existsByConfirmationCode(String confirmationCode);

    @Modifying
    @Query("UPDATE reservations SET guest_id = NULL WHERE guest_id = :guestId")
    void anonymizeByGuestId(@Param("guestId") Integer guestId);

    List<Reservation> findByFolioId(Integer folioId);

    @Query("""
            SELECT * FROM reservations r
            WHERE r.channel = 'WALK_IN' AND r.status = 'CONFIRMED'
            AND NOT EXISTS (
                SELECT 1 FROM folio_payments fp
                WHERE fp.folio_id = r.folio_id AND fp.kind = 'ROOM' AND fp.status = 'CAPTURED'
            )
            """)
    List<Reservation> findUnsettledWalkIn();

    @Query("SELECT EXISTS(SELECT 1 FROM reservations WHERE folio_id = :folioId AND id != :excludingReservationId AND status IN ('CONFIRMED', 'CHECKED_IN'))")
    boolean existsOtherActiveByFolioId(@Param("folioId") int folioId,
                                       @Param("excludingReservationId") int excludingReservationId);
}
