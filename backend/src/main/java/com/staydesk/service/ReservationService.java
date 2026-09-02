package com.staydesk.service;

import com.staydesk.exception.AlreadyCheckedInException;
import com.staydesk.exception.AlreadyCheckedOutException;
import com.staydesk.exception.CannotCancelException;
import com.staydesk.exception.CardPresentRecordOnlyDisabledException;
import com.staydesk.exception.DateConflictException;
import com.staydesk.exception.FolioNotFoundException;
import com.staydesk.exception.InvalidReservationException;
import com.staydesk.exception.NoReusableCredentialException;
import com.staydesk.exception.NoRoomAvailableException;
import com.staydesk.exception.PosDeviceNotFoundException;
import com.staydesk.exception.RateNotFoundException;
import com.staydesk.exception.ReservationNotFoundException;
import com.staydesk.exception.RoomNotFoundException;
import com.staydesk.exception.RoomTypeNotFoundException;
import com.staydesk.exception.RoomTypeUnavailableException;
import com.staydesk.exception.RoomUnavailableException;
import com.staydesk.model.EncryptedString;
import com.staydesk.model.Folio;
import com.staydesk.model.Guest;
import com.staydesk.model.Rate;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.model.ReusablePaymentCredential;
import com.staydesk.model.RoomType;
import com.staydesk.model.dto.CheckInResult;
import com.staydesk.model.dto.ExtendStayResult;
import com.staydesk.model.dto.ReservationEstimateResponse;
import com.staydesk.model.request.BacklogCheckInRequest;
import com.staydesk.provider.ProviderFactory;
import com.staydesk.repository.FolioRepository;
import com.staydesk.repository.GuestRepository;
import com.staydesk.repository.PosDeviceRepository;
import com.staydesk.repository.RateRepository;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.ReusablePaymentCredentialRepository;
import com.staydesk.repository.RoomRepository;
import com.staydesk.repository.RoomTypeRepository;
import com.staydesk.security.PiiCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final FolioRepository folioRepository;
    private final RateRepository rateRepository;
    private final PaymentService paymentService;
    private final FolioService folioService;
    private final GuestRepository guestRepository;
    private final SmsService smsService;
    private final LockPasscodeService lockPasscodeService;
    private final ProviderFactory providerFactory;
    private final PosDeviceRepository posDeviceRepository;
    private final PaymentCredentialService paymentCredentialService;
    private final PiiCipher piiCipher;
    private final ReusablePaymentCredentialRepository reusablePaymentCredentialRepository;

    private static final String BACKLOG_PLACEHOLDER_PHONE = "0000000000";
    private static final int STANDARD_CHECK_IN_HOUR = 15;

    public ReservationService(ReservationRepository reservationRepository, RoomRepository roomRepository,
                              RoomTypeRepository roomTypeRepository, FolioRepository folioRepository,
                              RateRepository rateRepository, PaymentService paymentService, FolioService folioService,
                              GuestRepository guestRepository, SmsService smsService,
                              LockPasscodeService lockPasscodeService, ProviderFactory providerFactory,
                              PosDeviceRepository posDeviceRepository,
                              PaymentCredentialService paymentCredentialService, PiiCipher piiCipher,
                              ReusablePaymentCredentialRepository reusablePaymentCredentialRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.folioRepository = folioRepository;
        this.rateRepository = rateRepository;
        this.paymentService = paymentService;
        this.folioService = folioService;
        this.guestRepository = guestRepository;
        this.smsService = smsService;
        this.lockPasscodeService = lockPasscodeService;
        this.providerFactory = providerFactory;
        this.posDeviceRepository = posDeviceRepository;
        this.paymentCredentialService = paymentCredentialService;
        this.piiCipher = piiCipher;
        this.reusablePaymentCredentialRepository = reusablePaymentCredentialRepository;
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

    private static long getTotalPeriods(Rate.RateType rateType, LocalDate checkInDate, LocalDate checkOutDate) {
        long totalPeriods = 0;

        if (rateType.equals(Rate.RateType.NIGHTLY)) {
            totalPeriods = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        } else if (rateType.equals(Rate.RateType.WEEKLY_5)) {
            totalPeriods = ChronoUnit.DAYS.between(checkInDate, checkOutDate) / 5;
        } else if (rateType.equals(Rate.RateType.WEEKLY_7)) {
            totalPeriods = ChronoUnit.DAYS.between(checkInDate, checkOutDate) / 7;
        }
        return totalPeriods;
    }

    /**
     * A guest with legacy pricing enabled has their flat override amount substituted for the
     * normal rate lookup, no matter which rate type they're booked under - it stands in for
     * rate.amount() everywhere billing math happens.
     */
    private BigDecimal resolveRateAmount(Integer guestId, Rate rate) {
        if (guestId == null) {
            return rate.amount();
        }

        return guestRepository.findById(guestId)
                              .filter(Guest::legacyPricing)
                              .map(Guest::legacyPricingAmount)
                              .filter(Objects::nonNull)
                              .orElse(rate.amount());
    }

    private String generateUniqueConfirmationCode() {
        String code;

        do {
            code = ConfirmationCodeGenerator.generate();
        } while (reservationRepository.existsByConfirmationCode(code));

        return code;
    }

    private BigDecimal computeFirstNightAmount(Reservation reservation) {
        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        BigDecimal amount = resolveRateAmount(reservation.guestId(), rate);

        BigDecimal baseAmount = switch (reservation.rateType()) {
            case NIGHTLY -> amount;
            case WEEKLY_5 -> amount.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
            case WEEKLY_7 -> amount.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
        };

        return folioService.estimateWithTax(baseAmount);
    }


    @Transactional
    public Reservation createReservation(Reservation reservation, String roomPaymentMethodId) {
        LocalDateTime now = LocalDateTime.now();

        RoomType roomType = roomTypeRepository.findById(reservation.roomTypeId())
                                              .orElseThrow(RoomTypeNotFoundException::new);

        if (!reservation.checkOutDate().isAfter(reservation.checkInDate())) {
            throw new InvalidReservationException();
        }

        int overlapping = reservationRepository.countOverlappingByRoomType(
                roomType.id(), reservation.checkOutDate(), reservation.checkInDate());

        if (overlapping >= roomType.availableCount()) {
            throw new RoomTypeUnavailableException();
        }

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        if ((reservation.rateType().equals(Rate.RateType.WEEKLY_5) && ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) % 5 != 0)
            || (reservation.rateType().equals(Rate.RateType.WEEKLY_7) && ChronoUnit.DAYS.between(reservation.checkInDate(), reservation.checkOutDate()) % 7 != 0)) {

            throw new InvalidReservationException();
        }

        String confirmationCode = generateUniqueConfirmationCode();

        Reservation savedReservation = reservationRepository.save(new Reservation(0, reservation.guestId(), null, roomType.id(),
                reservation.checkInDate(), reservation.checkOutDate(), reservation.status(), reservation.checkedInAt(),
                reservation.checkedOutAt(), reservation.rateType(), reservation.guestCount(), reservation.channel(), reservation.legalHold(), now, now,
                confirmationCode));

        Folio savedFolio = folioRepository.save(new Folio(0, savedReservation.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, now, now));

        BigDecimal rateAmount = resolveRateAmount(reservation.guestId(), rate);

        Folio folio = folioService.postCharge(savedFolio, "GUEST ROOM", rateAmount);

        BigDecimal estimatedStayAmount = folioService.estimateWithTax(
                rateAmount.multiply(BigDecimal.valueOf(getTotalPeriods(reservation.rateType(), reservation.checkInDate(), reservation.checkOutDate()))));

        if (savedReservation.channel().equals(Reservation.Channel.PHONE)) {
            paymentService.chargeFullStay(folio, estimatedStayAmount, providerFactory.getPaymentProviderName(), roomPaymentMethodId);
        }

        if (savedReservation.guestId() != null && savedReservation.channel() != Reservation.Channel.WALK_IN) {
            guestRepository.findById(savedReservation.guestId())
                           .filter(Guest::smsConsent)
                           .ifPresent(guest -> smsService.sendConfirmation(guest, savedReservation));
        }

        return savedReservation;
    }

    public ReservationEstimateResponse estimateTotal(Rate.RateType rateType, int guestCount, LocalDate checkInDate,
                                                     LocalDate checkOutDate, Integer guestId) {
        Rate rate = rateRepository.findByRateTypeAndGuestCount(rateType, guestCount)
                                  .orElseThrow(RateNotFoundException::new);

        BigDecimal rateAmount = resolveRateAmount(guestId, rate);
        BigDecimal subtotal = rateAmount.multiply(BigDecimal.valueOf(getTotalPeriods(rateType, checkInDate, checkOutDate)));
        BigDecimal total = folioService.estimateWithTax(subtotal);
        BigDecimal tax = total.subtract(subtotal);

        return new ReservationEstimateResponse(subtotal, tax, total);
    }

    @Transactional
    public Reservation updateReservation(int id, Reservation reservation) {
        Reservation existing = reservationRepository.findById(id)
                                                    .orElseThrow(ReservationNotFoundException::new);

        if (existing.roomId() != null) {
            boolean hasOverlap = reservationRepository.findOverlapping(existing.roomId(), reservation.checkOutDate(), reservation.checkInDate())
                                                      .stream()
                                                      .anyMatch(r -> r.id() != id);

            if (hasOverlap) {
                throw new DateConflictException();
            }
        } else {
            RoomType roomType = roomTypeRepository.findById(reservation.roomTypeId())
                                                  .orElseThrow(RoomTypeNotFoundException::new);

            int overlapping = reservationRepository.countOverlappingByRoomTypeExcludingReservation(
                    reservation.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate(), id);

            if (overlapping >= roomType.availableCount()) {
                throw new RoomTypeUnavailableException();
            }
        }

        if (!reservation.checkOutDate().isAfter(reservation.checkInDate())) {
            throw new InvalidReservationException();
        }

        Reservation updated = new Reservation(id, reservation.guestId(), existing.roomId(), reservation.roomTypeId(), reservation.checkInDate(),
                reservation.checkOutDate(), reservation.status(), reservation.checkedInAt(), reservation.checkedOutAt(),
                reservation.rateType(), reservation.guestCount(), existing.channel(), existing.legalHold(), reservation.createdAt(), LocalDateTime.now(),
                existing.confirmationCode());

        return reservationRepository.save(updated);
    }

    @Transactional
    public void deleteReservation(int id) {
        reservationRepository.findById(id)
                             .orElseThrow(ReservationNotFoundException::new);

        reservationRepository.deleteById(id);
    }

    public List<Room> getAvailableRoomsForCheckIn(int id) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        return roomRepository.findAvailableOfType(reservation.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate())
                             .stream()
                             .sorted(Comparator.comparingInt(Room::roomNumber))
                             .toList();
    }

    @Transactional
    public CheckInResult checkIn(int id, int roomId, String incidentalsPaymentMethodId, String roomPaymentMethodId) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new AlreadyCheckedInException();
        } else if (!reservation.status().equals(Reservation.ReservationStatus.CONFIRMED)) {
            throw new InvalidReservationException();
        }

        Room room = roomRepository.findAvailableOfType(reservation.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate())
                                  .stream()
                                  .filter(r -> r.id() == roomId)
                                  .findFirst()
                                  .orElseThrow(NoRoomAvailableException::new);

        reservationRepository.assignRoom(id, room.id());
        roomRepository.updateRoomStatus(room.id(), Room.RoomStatus.OCCUPIED);
        reservationRepository.updateReservationStatusToCheckedIn(id);

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        if (reservation.channel().equals(Reservation.Channel.WALK_IN)) {
            Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                      .orElseThrow(RateNotFoundException::new);

            BigDecimal rateAmount = resolveRateAmount(reservation.guestId(), rate);

            BigDecimal stayAmount = folioService.estimateWithTax(rateAmount.multiply(
                    BigDecimal.valueOf(
                            getTotalPeriods(reservation.rateType(), reservation.checkInDate(), reservation.checkOutDate()))));

            paymentService.chargeFullStay(folio, stayAmount, providerFactory.getPaymentProviderName(), roomPaymentMethodId);
        }

        paymentService.createIncidentalHold(folio, providerFactory.getPaymentProviderName(), incidentalsPaymentMethodId);

        Reservation checkedIn = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);

        LockPasscodeService.PasscodeResult passcodeResult = lockPasscodeService.issuePasscode(checkedIn, room);

        if (passcodeResult.outcome() == LockPasscodeService.PasscodeResult.Outcome.ISSUED && checkedIn.guestId() != null) {
            guestRepository.findById(checkedIn.guestId())
                           .filter(Guest::smsConsent)
                           .ifPresent(guest -> smsService.sendCheckInComplete(guest, room.roomNumber(), passcodeResult.passcode()));
        }

        return new CheckInResult(checkedIn, passcodeResult.outcome());
    }

    @Transactional
    public CheckInResult checkInTerminal(int id, int roomId, Integer posDeviceId) {
        String paymentMethodToken;

        if (posDeviceId != null) {
            paymentMethodToken = posDeviceRepository.findById(posDeviceId)
                                                    .orElseThrow(PosDeviceNotFoundException::new)
                                                    .deviceId();
        } else if (providerFactory.isCardPresentRecordOnly()) {
            paymentMethodToken = "no-device-record-only";
        } else {
            throw new CardPresentRecordOnlyDisabledException();
        }

        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new AlreadyCheckedInException();
        } else if (!reservation.status().equals(Reservation.ReservationStatus.CONFIRMED)) {
            throw new InvalidReservationException();
        }

        Room room = roomRepository.findAvailableOfType(reservation.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate())
                                  .stream()
                                  .filter(r -> r.id() == roomId)
                                  .findFirst()
                                  .orElseThrow(NoRoomAvailableException::new);

        reservationRepository.assignRoom(id, room.id());
        roomRepository.updateRoomStatus(room.id(), Room.RoomStatus.OCCUPIED);
        reservationRepository.updateReservationStatusToCheckedIn(id);

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        if (reservation.channel().equals(Reservation.Channel.WALK_IN)) {
            Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                      .orElseThrow(RateNotFoundException::new);

            BigDecimal rateAmount = resolveRateAmount(reservation.guestId(), rate);

            BigDecimal stayAmount = folioService.estimateWithTax(rateAmount.multiply(
                    BigDecimal.valueOf(
                            getTotalPeriods(reservation.rateType(), reservation.checkInDate(), reservation.checkOutDate()))));

            paymentService.chargeFullStay(folio, stayAmount, providerFactory.getCardPresentProviderName(), paymentMethodToken);
        }

        paymentService.createIncidentalHold(folio, providerFactory.getCardPresentProviderName(), paymentMethodToken);

        Reservation checkedIn = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);

        LockPasscodeService.PasscodeResult passcodeResult = lockPasscodeService.issuePasscode(checkedIn, room);

        if (passcodeResult.outcome() == LockPasscodeService.PasscodeResult.Outcome.ISSUED && checkedIn.guestId() != null) {
            guestRepository.findById(checkedIn.guestId())
                           .filter(Guest::smsConsent)
                           .ifPresent(guest -> smsService.sendCheckInComplete(guest, room.roomNumber(), passcodeResult.passcode()));
        }

        return new CheckInResult(checkedIn, passcodeResult.outcome());
    }

    @Transactional
    public Reservation backlogCheckIn(BacklogCheckInRequest request) {
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new InvalidReservationException();
        }

        Room room = roomRepository.findById(request.roomId()).orElseThrow(RoomNotFoundException::new);

        if (room.status() == Room.RoomStatus.OCCUPIED) {
            throw new RoomUnavailableException();
        }

        Guest guest = findOrCreateBacklogGuest(request);

        LocalDateTime now = LocalDateTime.now();

        String confirmationCode = generateUniqueConfirmationCode();

        Rate.RateType rateType = request.rateType() != null ? request.rateType() : Rate.RateType.NIGHTLY;
        int guestCount = request.guestCount() != null ? request.guestCount() : 1;

        Reservation savedReservation = reservationRepository.save(new Reservation(0, guest.id(), room.id(), room.roomTypeId(),
                request.checkInDate(), request.checkOutDate(), Reservation.ReservationStatus.CHECKED_IN,
                request.checkInDate().atTime(STANDARD_CHECK_IN_HOUR, 0), null, rateType, guestCount,
                Reservation.Channel.WALK_IN, false, now, now, confirmationCode));

        folioRepository.save(new Folio(0, savedReservation.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, now, now));

        roomRepository.updateRoomStatus(room.id(), Room.RoomStatus.OCCUPIED);

        return savedReservation;
    }

    private Guest findOrCreateBacklogGuest(BacklogCheckInRequest request) {
        String email = request.email() != null && !request.email().isBlank()
                ? request.email().strip().toLowerCase()
                : "backlog." + UUID.randomUUID() + "@placeholder.martinhousemotel.local";

        String phoneNumber = request.phoneNumber() != null && !request.phoneNumber().isBlank()
                ? request.phoneNumber()
                : BACKLOG_PLACEHOLDER_PHONE;

        String emailHash = piiCipher.hash(email);

        return guestRepository.findByEmailHash(emailHash).orElseGet(() -> {
            LocalDateTime createdAt = LocalDateTime.now();

            return guestRepository.save(new Guest(0, new EncryptedString(request.firstName()), new EncryptedString(request.lastName()),
                    new EncryptedString(email), emailHash, new EncryptedString(phoneNumber), false,
                    false, null, null, null, false, false, null, createdAt, createdAt));
        });
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

        BigDecimal rateAmount = resolveRateAmount(reservation.guestId(), rate);

        long remainingPeriods = getRemainingPeriods(reservation);

        for (long i = 0; i < remainingPeriods; i++) {
            folio = folioService.postCharge(folio, "GUEST ROOM", rateAmount);
        }

        folioRepository.save(new Folio(folio.id(), folio.reservationId(), Folio.FolioStatus.CLOSED, folio.total(), folio.paidAt(), folio.createdAt(), now));

        paymentCredentialService.scheduleExpiry(folio.id(), now.plusDays(30));

        return reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
    }

    @Transactional
    public ExtendStayResult extendStay(int id, LocalDate newCheckOutDate) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new InvalidReservationException();
        }

        if (!newCheckOutDate.isAfter(reservation.checkOutDate())) {
            throw new InvalidReservationException();
        }

        long totalNights = ChronoUnit.DAYS.between(reservation.checkInDate(), newCheckOutDate);

        if ((reservation.rateType().equals(Rate.RateType.WEEKLY_5) && totalNights % 5 != 0)
            || (reservation.rateType().equals(Rate.RateType.WEEKLY_7) && totalNights % 7 != 0)) {
            throw new InvalidReservationException();
        }

        boolean hasConflict = reservationRepository.findOverlapping(reservation.roomId(), newCheckOutDate, reservation.checkOutDate())
                                                    .stream()
                                                    .anyMatch(r -> r.id() != id);

        if (hasConflict) {
            throw new DateConflictException();
        }

        long additionalPeriods = getTotalPeriods(reservation.rateType(), reservation.checkInDate(), newCheckOutDate)
                                  - getTotalPeriods(reservation.rateType(), reservation.checkInDate(), reservation.checkOutDate());

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();

        ReusablePaymentCredential credential = reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(folio.id())
                .stream()
                .filter(c -> c.expiresAt() == null || c.expiresAt().isAfter(now))
                .findFirst()
                .orElseThrow(NoReusableCredentialException::new);

        BigDecimal chargeAmount = folioService.estimateWithTax(rate.amount().multiply(BigDecimal.valueOf(additionalPeriods)));

        paymentService.chargeStoredCredential(folio, credential, chargeAmount, "Stay extension to " + newCheckOutDate);

        Reservation extended = new Reservation(id, reservation.guestId(), reservation.roomId(), reservation.roomTypeId(),
                reservation.checkInDate(), newCheckOutDate, reservation.status(), reservation.checkedInAt(), reservation.checkedOutAt(),
                reservation.rateType(), reservation.guestCount(), reservation.channel(), reservation.legalHold(),
                reservation.createdAt(), now, reservation.confirmationCode());

        Reservation saved = reservationRepository.save(extended);

        return new ExtendStayResult(saved, chargeAmount);
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

        return reservationRepository.save(new Reservation(id, reservation.guestId(), reservation.roomId(), reservation.roomTypeId(),
                reservation.checkInDate(), reservation.checkOutDate(), Reservation.ReservationStatus.CANCELLED, reservation.checkedInAt(),
                reservation.checkedOutAt(), reservation.rateType(), reservation.guestCount(), reservation.channel(), reservation.legalHold(),
                reservation.createdAt(), LocalDateTime.now(), reservation.confirmationCode()));
    }

    @Transactional
    public Reservation markNoShow(int id) {
        Reservation reservation = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);

        if (!reservation.status().equals(Reservation.ReservationStatus.CONFIRMED)
            || !reservation.channel().equals(Reservation.Channel.PHONE)) {
            throw new InvalidReservationException();
        }

        BigDecimal firstNightAmount = computeFirstNightAmount(reservation);

        folioRepository.getFolioByReservationId(reservation.id())
                       .ifPresent(f -> {
                           paymentService.refundAllButFirstNight(f, firstNightAmount);
                           folioRepository.closeFolio(f.id());
                       });

        return reservationRepository.save(new Reservation(id, reservation.guestId(), reservation.roomId(), reservation.roomTypeId(),
                reservation.checkInDate(), reservation.checkOutDate(), Reservation.ReservationStatus.NO_SHOW, reservation.checkedInAt(),
                reservation.checkedOutAt(), reservation.rateType(), reservation.guestCount(), reservation.channel(), reservation.legalHold(),
                reservation.createdAt(), LocalDateTime.now(), reservation.confirmationCode()));
    }

    @Transactional
    public Reservation setLegalHold(int id) {
        reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
        reservationRepository.setLegalHold(id);
        return reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
    }

    @Transactional
    public Reservation clearLegalHold(int id) {
        reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
        reservationRepository.clearLegalHold(id);
        return reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
    }
}
