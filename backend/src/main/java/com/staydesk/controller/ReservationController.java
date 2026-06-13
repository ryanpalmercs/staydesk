package com.staydesk.controller;

import com.staydesk.exception.DateConflictException;
import com.staydesk.exception.ReservationNotFoundException;
import com.staydesk.exception.RoomNotFoundException;
import com.staydesk.exception.RoomUnavailableException;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.RoomRepository;
import com.staydesk.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final ReservationService reservationService;

    public ReservationController(ReservationRepository reservationRepository, RoomRepository roomRepository, ReservationService reservationService) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.reservationService = reservationService;
    }

    @GetMapping
    public List<Reservation> getReservations() {
        LOGGER.info("Getting all reservations");
        return reservationRepository.findAll();
    }

    @GetMapping("{id}")
    public ResponseEntity<Reservation> getReservation(@PathVariable Integer id) {
        LOGGER.info("Getting reservation with id {}", id);

        return reservationRepository.findById(id)
                                    .map(ResponseEntity::ok)
                                    .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Reservation> createReservation(@RequestBody Reservation reservation) {
        LOGGER.info("Creating reservation {}", reservation);

        try {
            Reservation savedReservation = reservationService.createReservation(reservation);
            URI location = URI.create("/reservations/" + savedReservation.id());
            return ResponseEntity.created(location).body(savedReservation);
        } catch (RoomNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (RoomUnavailableException | DateConflictException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("{id}")
    public ResponseEntity<Reservation> updateReservation(@PathVariable Integer id,
                                                         @RequestBody Reservation reservation) {
        LOGGER.info("Updating reservation {}", reservation);

        if (!reservationRepository.existsById(id)) {
            LOGGER.warn("Reservation with id {} does not exist", id);
            return ResponseEntity.notFound().build();
        }

        Reservation updatedReservation = new Reservation(id, reservation.guestId(), reservation.roomId(),
                reservation.checkInDate(), reservation.checkOutDate(), reservation.status(), reservation.checkedInAt(),
                reservation.checkedOutAt(), reservation.createdAt(), LocalDateTime.now());

        return ResponseEntity.ok(reservationRepository.save(updatedReservation));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteReservation(@PathVariable Integer id) {
        LOGGER.info("Deleting reservation with id {}", id);

        try {
            reservationService.deleteReservation(id);
            return ResponseEntity.noContent().build();
        } catch (ReservationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
