package com.staydesk.controller;

import com.staydesk.exception.AlreadyCheckedInException;
import com.staydesk.exception.AlreadyCheckedOutException;
import com.staydesk.exception.CannotCancelException;
import com.staydesk.exception.DateConflictException;
import com.staydesk.exception.FolioNotFoundException;
import com.staydesk.exception.InvalidReservationException;
import com.staydesk.exception.NoRoomAvailableException;
import com.staydesk.exception.PosDeviceNotFoundException;
import com.staydesk.exception.RateNotFoundException;
import com.staydesk.exception.ReservationNotFoundException;
import com.staydesk.exception.RoomNotFoundException;
import com.staydesk.exception.RoomTypeNotFoundException;
import com.staydesk.exception.RoomTypeUnavailableException;
import com.staydesk.model.Rate;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.model.dto.CheckInResult;
import com.staydesk.model.dto.ReservationEstimateResponse;
import com.staydesk.model.request.CheckInRequest;
import com.staydesk.model.request.CreateReservationRequest;
import com.staydesk.model.request.TerminalCheckInRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public ResponseEntity<Reservation> createReservation(@RequestBody CreateReservationRequest request) {
        LOGGER.info("Creating reservation {}", request);

        try {
            Reservation savedReservation = reservationService.createReservation(
                    new Reservation(0, request.guestId(), null, request.roomTypeId(), request.checkInDate(),
                            request.checkOutDate(), Reservation.ReservationStatus.CONFIRMED, null,
                            null, request.rateType(), request.guestCount(), request.channel(), false, LocalDateTime.now(), LocalDateTime.now(), null),
                    request.roomPaymentMethodId());
            URI location = URI.create("/reservations/" + savedReservation.id());
            return ResponseEntity.created(location).body(savedReservation);
        } catch (RoomTypeNotFoundException | RateNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (RoomTypeUnavailableException | DateConflictException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("{id}")
    public ResponseEntity<Reservation> updateReservation(@PathVariable Integer id,
                                                         @RequestBody Reservation reservation) {
        LOGGER.info("Updating reservation {}", reservation);

        try {
            return ResponseEntity.ok(reservationService.updateReservation(id, reservation));
        } catch (ReservationNotFoundException | RoomTypeNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (DateConflictException | RoomTypeUnavailableException e) {
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

    @GetMapping("{id}/available-rooms")
    public ResponseEntity<List<Room>> getAvailableRoomsForCheckIn(@PathVariable Integer id) {
        LOGGER.info("Getting available rooms for check-in of reservation {}", id);

        try {
            return ResponseEntity.ok(reservationService.getAvailableRoomsForCheckIn(id));
        } catch (ReservationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("{id}/check-in")
    public ResponseEntity<CheckInResult> checkIn(@PathVariable Integer id, @RequestBody CheckInRequest request) {
        LOGGER.info("Checking reservation in with id {}", id);

        try {
            return ResponseEntity.ok(reservationService.checkIn(id, request.roomId(), request.incidentalsPaymentMethodId()));
        } catch (RoomNotFoundException | ReservationNotFoundException | RateNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AlreadyCheckedInException | NoRoomAvailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (InvalidReservationException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("An error occurred while checking reservation in with id {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("{id}/check-in/terminal")
    public ResponseEntity<CheckInResult> checkInTerminal(@PathVariable int id,
                                                         @RequestBody TerminalCheckInRequest request) {
        LOGGER.info("Checking reservation in via terminal with id {}", id);

        try {
            return ResponseEntity.ok(reservationService.checkInTerminal(id, request.roomId(), request.posDeviceId()));
        } catch (PosDeviceNotFoundException | RoomNotFoundException | ReservationNotFoundException | RateNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (AlreadyCheckedInException | NoRoomAvailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (InvalidReservationException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("An error occurred while checking reservation in via terminal with id {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("{id}/check-out")
    public ResponseEntity<Reservation> checkOut(@PathVariable Integer id) {
        LOGGER.info("Checking reservation out with id {}", id);

        try {
            return ResponseEntity.ok(reservationService.checkOut(id));
        } catch (ReservationNotFoundException | RoomNotFoundException | FolioNotFoundException |
                 RateNotFoundException e) {
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

    @PostMapping("{id}/legal-hold")
    public ResponseEntity<Reservation> setLegalHold(@PathVariable Integer id) {
        LOGGER.info("Placing legal hold on reservation {}", id);

        try {
            return ResponseEntity.ok(reservationService.setLegalHold(id));
        } catch (ReservationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("{id}/legal-hold")
    public ResponseEntity<Reservation> clearLegalHold(@PathVariable Integer id) {
        LOGGER.info("Clearing legal hold on reservation {}", id);

        try {
            return ResponseEntity.ok(reservationService.clearLegalHold(id));
        } catch (ReservationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/estimate")
    public ResponseEntity<ReservationEstimateResponse> getEstimate(@RequestParam Rate.RateType rateType,
                                                                   @RequestParam int guestCount,
                                                                   @RequestParam LocalDate checkInDate,
                                                                   @RequestParam LocalDate checkOutDate) {
        LOGGER.info("Estimating total for rateType={} guestCount={} {} to {}", rateType, guestCount, checkInDate, checkOutDate);

        try {
            return ResponseEntity.ok(reservationService.estimateTotal(rateType, guestCount, checkInDate, checkOutDate));
        } catch (RateNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
