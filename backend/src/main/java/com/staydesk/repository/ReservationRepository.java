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

    @Query("SELECT COUNT(*) FROM reservations WHERE room_type_id = :roomTypeId AND check_in_date < :checkOut AND check_out_date > :checkIn AND status NOT IN ('CANCELLED', 'CHECKED_OUT', 'NO_SHOW')")
    int countOverlappingByRoomType(@Param("roomTypeId") int roomTypeId, @Param("checkOut") LocalDate checkOut,
                                   @Param("checkIn") LocalDate checkIn);

    @Query("SELECT COUNT(*) FROM reservations WHERE room_type_id = :roomTypeId AND id != :excludingReservationId AND check_in_date < :checkOut AND check_out_date > :checkIn AND status NOT IN ('CANCELLED', 'CHECKED_OUT', 'NO_SHOW')")
    int countOverlappingByRoomTypeExcludingReservation(@Param("roomTypeId") int roomTypeId,
                                                       @Param("checkOut") LocalDate checkOut,
                                                       @Param("checkIn") LocalDate checkIn,
                                                       @Param("excludingReservationId") int excludingReservationId);

    @Query("SELECT * FROM reservations WHERE status = 'CONFIRMED' AND channel = 'PHONE' AND check_in_date < :date")
    List<Reservation> findNoShowCandidates(@Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE reservations SET room_id = :roomId WHERE id = :id")
    void assignRoom(@Param("id") Integer id, @Param("roomId") Integer roomId);

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
}
