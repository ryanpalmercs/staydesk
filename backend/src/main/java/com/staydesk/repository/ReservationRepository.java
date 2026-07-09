package com.staydesk.repository;

import com.staydesk.model.Reservation;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends ListCrudRepository<Reservation, Integer> {

    @Query("SELECT * FROM reservations WHERE room_id = :roomId AND check_in_date < :checkOut AND check_out_date > :checkIn AND status NOT IN ('CANCELLED', 'CHECKED_OUT')")
    List<Reservation> findOverlapping(@Param("roomId") int roomId, @Param("checkOut") LocalDate checkOut, @Param("checkIn") LocalDate checkIn);

    @Query("SELECT * FROM reservations WHERE room_id = :roomId AND status NOT IN ('CANCELLED', 'CHECKED_OUT') ORDER BY check_in_date")
    List<Reservation> findActiveByRoomId(@Param("roomId") int roomId);

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

    @Modifying
    @Query("UPDATE reservations SET guest_id = NULL WHERE guest_id = :guestId")
    void anonymizeByGuestId(@Param("guestId") Integer guestId);
}
