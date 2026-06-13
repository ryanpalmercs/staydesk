package com.staydesk.service;

import com.staydesk.exception.DateConflictException;
import com.staydesk.exception.ReservationNotFoundException;
import com.staydesk.exception.RoomNotFoundException;
import com.staydesk.exception.RoomUnavailableException;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;

    public ReservationService(ReservationRepository reservationRepository, RoomRepository roomRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Reservation createReservation(Reservation reservation) {
        Room room = roomRepository.findById(reservation.roomId())
                                  .orElseThrow(RoomNotFoundException::new);

        if (room.status() == Room.RoomStatus.RESERVED || room.status() == Room.RoomStatus.MAINTENANCE) {
            throw new RoomUnavailableException();
        }

        if (!reservationRepository.findOverlapping(reservation.roomId(), reservation.checkInDate(), reservation.checkOutDate()).isEmpty()) {
            throw new DateConflictException();
        }

        roomRepository.save(new Room(room.id(), room.roomNumber(), room.type(), room.nightlyRate(),
                Room.RoomStatus.RESERVED, room.createdAt(), LocalDateTime.now()));

        return reservationRepository.save(reservation);
    }

    @Transactional
    public void deleteReservation(int id) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        reservationRepository.deleteById(id);

        roomRepository.findById(reservation.roomId())
                      .ifPresent(room -> roomRepository.save(new Room(room.id(), room.roomNumber(), room.type(),
                              room.nightlyRate(), Room.RoomStatus.AVAILABLE, room.createdAt(), LocalDateTime.now())));
    }
}
