package com.staydesk.controller;

import com.staydesk.exception.AlreadyCheckedInException;
import com.staydesk.exception.AlreadyCheckedOutException;
import com.staydesk.exception.CannotCancelException;
import com.staydesk.exception.DateConflictException;
import com.staydesk.exception.FolioNotFoundException;
import com.staydesk.exception.InvalidReservationException;
import com.staydesk.exception.RateNotFoundException;
import com.staydesk.exception.ReservationNotFoundException;
import com.staydesk.exception.RoomNotFoundException;
import com.staydesk.exception.RoomUnavailableException;
import com.staydesk.model.Reservation;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
import java.util.List;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    public ReservationController(ReservationRepository reservationRepository, ReservationService reservationService) {
        this.reservationRepository = reservationRepository;
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
        } catch (RoomNotFoundException | RateNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (RoomUnavailableException | DateConflictException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("{id}")
    public ResponseEntity<Reservation> updateReservation(@PathVariable Integer id,
                                                         @RequestBody Reservation reservation) {
        LOGGER.info("Updating reservation {}", reservation);

        try {
            return ResponseEntity.ok(reservationService.updateReservation(id, reservation));
        } catch (ReservationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (DateConflictException e) {
            return ResponseEntity.badRequest().build();
        }
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

    @PostMapping("{id}/check-in")
    public ResponseEntity<Reservation> checkIn(@PathVariable Integer id) {
        LOGGER.info("Checking reservation in with id {}", id);

        try {
            return ResponseEntity.ok(reservationService.checkIn(id));
        } catch (RoomNotFoundException | ReservationNotFoundException | RateNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AlreadyCheckedInException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (InvalidReservationException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("An error occurred while checking reservation in with id {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("{id}/check-out")
    public ResponseEntity<Reservation> checkOut(@PathVariable Integer id) {
        LOGGER.info("Checking reservation out with id {}", id);

        try {
            return ResponseEntity.ok(reservationService.checkOut(id));
        } catch (ReservationNotFoundException | RoomNotFoundException | FolioNotFoundException | RateNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AlreadyCheckedOutException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (InvalidReservationException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("An error occurred while checking reservation out with id {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("{id}/cancel")
    public ResponseEntity<Reservation> cancelReservation(@PathVariable Integer id) {
        LOGGER.info("Canceling reservation with id {}", id);

        try {
            return ResponseEntity.ok(reservationService.cancelReservation(id));
        } catch (ReservationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (CannotCancelException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("An error occurred while canceling reservation with id {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
