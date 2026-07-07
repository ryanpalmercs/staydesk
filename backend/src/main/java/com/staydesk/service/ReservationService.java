package com.staydesk.service;

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
import com.staydesk.model.Folio;
import com.staydesk.model.Rate;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.model.dto.CheckInResult;
import com.staydesk.repository.FolioRepository;
import com.staydesk.repository.GuestRepository;
import com.staydesk.repository.RateRepository;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final FolioRepository folioRepository;
    private final RateRepository rateRepository;
    private final PaymentService paymentService;
    private final FolioService folioService;
    private final GuestRepository guestRepository;
    private final SmsService smsService;
    private final LockPasscodeService lockPasscodeService;

    public ReservationService(ReservationRepository reservationRepository, RoomRepository roomRepository,
                              FolioRepository folioRepository, RateRepository rateRepository,
                              PaymentService paymentService, FolioService folioService, GuestRepository guestRepository,
                              SmsService smsService, LockPasscodeService lockPasscodeService) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.folioRepository = folioRepository;
        this.rateRepository = rateRepository;
        this.paymentService = paymentService;
        this.folioService = folioService;
        this.guestRepository = guestRepository;
        this.smsService = smsService;
        this.lockPasscodeService = lockPasscodeService;
    }

    private static long getRemainingPeriods(Reservation reservation) {
        long remainingPeriods = 0;

        if (reservation.rateType().equals(Rate.RateType.NIGHTLY)) {
            remainingPeriods = ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) - 1;
        } else if (reservation.rateType().equals(Rate.RateType.WEEKLY_5)) {
            remainingPeriods = (ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) - 5) / 5;
        } else if (reservation.rateType().equals(Rate.RateType.WEEKLY_7)) {
            remainingPeriods = (ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) - 7) / 7;
        }
        return remainingPeriods;
    }

    private static long getTotalPeriods(Reservation reservation) {
        long totalPeriods = 0;

        if (reservation.rateType().equals(Rate.RateType.NIGHTLY)) {
            totalPeriods = ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate());
        } else if (reservation.rateType().equals(Rate.RateType.WEEKLY_5)) {
            totalPeriods = ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) / 5;
        } else if (reservation.rateType().equals(Rate.RateType.WEEKLY_7)) {
            totalPeriods = ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) / 7;
        }
        return totalPeriods;
    }

    @Transactional
    public Reservation createReservation(Reservation reservation, String roomPaymentMethodId) {
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

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        if ((reservation.rateType().equals(Rate.RateType.WEEKLY_5) && ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) % 5 != 0)
            || (reservation.rateType().equals(Rate.RateType.WEEKLY_7) && ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) % 7 != 0)) {

            throw new InvalidReservationException();
        }

        Reservation savedReservation = reservationRepository.save(new Reservation(0, reservation.guestId(), reservation.roomId(),
                reservation.checkInDate(), reservation.checkOutDate(), reservation.status(), reservation.checkedInAt(),
                reservation.checkedOutAt(), reservation.rateType(), reservation.guestCount(), now, now));

        Folio savedFolio = folioRepository.save(new Folio(0, savedReservation.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, now, now));

        Folio folio = folioService.postCharge(savedFolio, "GUEST ROOM", rate.amount());

        BigDecimal estimatedStayAmount = folioService.estimateWithTax(
                rate.amount().multiply(BigDecimal.valueOf(getTotalPeriods(reservation))));

        paymentService.createRoomHold(folio, estimatedStayAmount, roomPaymentMethodId);

        guestRepository.findById(savedReservation.guestId()).ifPresent(guest -> smsService.sendConfirmation(guest, savedReservation, room.roomNumber()));

        return savedReservation;
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
                reservation.rateType(), reservation.guestCount(), reservation.createdAt(), LocalDateTime.now());

        return reservationRepository.save(updated);
    }

    @Transactional
    public void deleteReservation(int id) {
        reservationRepository.findById(id)
                             .orElseThrow(ReservationNotFoundException::new);

        reservationRepository.deleteById(id);
    }

    @Transactional
    public CheckInResult checkIn(int id, String incidentalsPaymentMethodId) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new AlreadyCheckedInException();
        } else if (!reservation.status().equals(Reservation.ReservationStatus.CONFIRMED)) {
            throw new InvalidReservationException();
        }

        roomRepository.updateRoomStatus(reservation.roomId(), Room.RoomStatus.OCCUPIED);
        reservationRepository.updateReservationStatusToCheckedIn(id);

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);
        paymentService.createIncidentalHold(folio, incidentalsPaymentMethodId);

        Room room = roomRepository.findById(reservation.roomId()).orElseThrow(RoomNotFoundException::new);

        LockPasscodeService.PasscodeResult passcodeResult = lockPasscodeService.issuePasscode(reservation, room);

        if (passcodeResult.outcome() == LockPasscodeService.PasscodeResult.Outcome.ISSUED) {
            guestRepository.findById(reservation.guestId())
                           .ifPresent(guest -> smsService.sendCheckInComplete(guest, reservation, room.roomNumber(), passcodeResult.passcode()));
        }

        Reservation updated = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
        return new CheckInResult(updated, passcodeResult.outcome());
    }

    @Transactional
    public Reservation checkOut(int id) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();

        if (reservation.status().equals(Reservation.ReservationStatus.CHECKED_OUT)) {
            throw new AlreadyCheckedOutException();
        } else if (!reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new InvalidReservationException();
        }

        reservationRepository.updateReservationStatusToCheckedOut(id);

        roomRepository.updateRoomStatus(reservation.roomId(), Room.RoomStatus.AVAILABLE);

        lockPasscodeService.revokePasscodes(id);

        Folio folio = folioRepository.getFolioByReservationId(reservation.id())
                                     .orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long remainingPeriods = getRemainingPeriods(reservation);

        for (long i = 0; i < remainingPeriods; i++) {
            folio = folioService.postCharge(folio, "GUEST ROOM", rate.amount());
        }

        folioRepository.save(new Folio(folio.id(), folio.reservationId(), Folio.FolioStatus.CLOSED, folio.total(), folio.paidAt(), folio.createdAt(), now));

        return reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
    }

    @Transactional
    public Reservation cancelReservation(int id) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status().equals(Reservation.ReservationStatus.CHECKED_OUT) || reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new CannotCancelException();
        }

        folioRepository.getFolioByReservationId(reservation.id())
                       .ifPresent(f -> {
                           paymentService.cancelOpenHolds(f);
                           folioRepository.closeFolio(f.id());
                       });

        return reservationRepository.save(new Reservation(id, reservation.guestId(), reservation.roomId(), reservation.checkInDate(),
                reservation.checkOutDate(), Reservation.ReservationStatus.CANCELLED, reservation.checkedInAt(), reservation.checkedOutAt(),
                reservation.rateType(), reservation.guestCount(), reservation.createdAt(), LocalDateTime.now()));
    }
}
