package com.staydesk.service;

import com.staydesk.exception.AlreadyCheckedInException;
import com.staydesk.exception.DateConflictException;
import com.staydesk.exception.InvalidReservationException;
import com.staydesk.exception.ReservationNotFoundException;
import com.staydesk.exception.RoomNotFoundException;
import com.staydesk.exception.RoomUnavailableException;
import com.staydesk.model.Folio;
import com.staydesk.model.FolioItem;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.repository.FolioItemRepository;
import com.staydesk.repository.FolioRepository;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final FolioRepository folioRepository;
    private final FolioItemRepository folioItemRepository;

    public ReservationService(ReservationRepository reservationRepository, RoomRepository roomRepository,
                              FolioRepository folioRepository, FolioItemRepository folioItemRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.folioRepository = folioRepository;
        this.folioItemRepository = folioItemRepository;
    }

    @Transactional
    public Reservation createReservation(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();

        Room room = roomRepository.findById(reservation.roomId())
                                  .orElseThrow(RoomNotFoundException::new);

        if (room.status() == Room.RoomStatus.MAINTENANCE) {
            throw new RoomUnavailableException();
        }

        if (!reservation.checkOutDate().isAfter(reservation.checkInDate())) {
            throw new InvalidReservationException();
        }

        if (!reservationRepository.findOverlapping(reservation.roomId(), reservation.checkOutDate(), reservation.checkInDate()).isEmpty()) {
            throw new DateConflictException();
        }

        Reservation savedReservation = new Reservation(0, reservation.guestId(), reservation.roomId(),
                reservation.checkInDate(), reservation.checkOutDate(), reservation.status(), reservation.checkedInAt(),
                reservation.checkedOutAt(), now, now);

        return reservationRepository.save(savedReservation);
    }

    @Transactional
    public Reservation updateReservation(int id, Reservation reservation) {
        reservationRepository.findById(id)
                             .orElseThrow(ReservationNotFoundException::new);

        boolean hasOverlap = reservationRepository.findOverlapping(reservation.roomId(), reservation.checkOutDate(), reservation.checkInDate())
                                                  .stream()
                                                  .anyMatch(r -> r.id() != id);

        if (hasOverlap) {
            throw new DateConflictException();
        }

        if (!reservation.checkOutDate().isAfter(reservation.checkInDate())) {
            throw new InvalidReservationException();
        }

        Reservation updated = new Reservation(id, reservation.guestId(), reservation.roomId(), reservation.checkInDate(),
                reservation.checkOutDate(), reservation.status(), reservation.checkedInAt(), reservation.checkedOutAt(),
                reservation.createdAt(), LocalDateTime.now());

        return reservationRepository.save(updated);
    }

    @Transactional
    public void deleteReservation(int id) {
        reservationRepository.findById(id)
                             .orElseThrow(ReservationNotFoundException::new);

        reservationRepository.deleteById(id);
    }

    @Transactional
    public Reservation checkIn(int id) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();

        if (reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new AlreadyCheckedInException();
        } else if (!reservation.status().equals(Reservation.ReservationStatus.CONFIRMED)) {
            throw new InvalidReservationException();
        }

        Room room = roomRepository.findById(reservation.roomId())
                                          .orElseThrow(RoomNotFoundException::new);

        roomRepository.updateRoomStatus(reservation.roomId(), Room.RoomStatus.OCCUPIED);

        reservationRepository.updateReservationToCheckedIn(id);

        Folio savedFolio = new Folio(0, id, Folio.FolioStatus.OPEN, room.nightlyRate(), now, now);

        Folio folio = folioRepository.save(savedFolio);

        FolioItem folioItem = new FolioItem(0, folio.id(), "GUEST ROOM", room.nightlyRate(), FolioItem.FolioItemType.CHARGE, now, now);

        folioItemRepository.save(folioItem);

        return reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
    }
}
