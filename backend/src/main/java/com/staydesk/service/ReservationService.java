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
import com.staydesk.exception.StayAlreadySettledException;
import com.staydesk.exception.RoomUnavailableException;
import com.staydesk.model.EncryptedString;
import com.staydesk.model.Folio;
import com.staydesk.model.Guest;
import com.staydesk.model.Rate;
import com.staydesk.model.RateOverride;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.model.ReusablePaymentCredential;
import com.staydesk.model.RoomType;
import com.staydesk.model.dto.CheckInEstimateResponse;
import com.staydesk.model.dto.CheckInResult;
import com.staydesk.model.dto.ExtendStayResult;
import com.staydesk.model.dto.ReservationEstimateResponse;
import com.staydesk.model.request.CreateMultiRoomReservationRequest;
import com.staydesk.model.dto.SyncFoliosResult;
import com.staydesk.model.request.BacklogCheckInRequest;
import com.staydesk.provider.ProviderFactory;
import com.staydesk.repository.FolioRepository;
import com.staydesk.repository.GuestRepository;
import com.staydesk.repository.PosDeviceRepository;
import com.staydesk.repository.RateOverrideRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final FolioRepository folioRepository;
    private final RateRepository rateRepository;
    private final RateOverrideRepository rateOverrideRepository;
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
                              RateRepository rateRepository, RateOverrideRepository rateOverrideRepository,
                              PaymentService paymentService, FolioService folioService,
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
        this.rateOverrideRepository = rateOverrideRepository;
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

    private static long getTotalNights(LocalDate checkInDate, LocalDate checkOutDate) {
        return ChronoUnit.DAYS.between(checkInDate, checkOutDate);
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
     * The tier that governs a stay's nightly rate, driven purely by total length of stay: 1-4
     * nights pay the standard nightly rate, 5-6 nights pay the 5-night rate divided evenly across
     * the stay, 7+ nights pay the 7-night rate divided evenly across the stay.
     */
    private static Rate.RateType tierForNights(long nights) {
        if (nights >= 7) {
            return Rate.RateType.WEEKLY_7;
        } else if (nights >= 5) {
            return Rate.RateType.WEEKLY_5;
        }
        return Rate.RateType.NIGHTLY;
    }

    /**
     * A guest with legacy pricing enabled has their flat override amount substituted for the
     * normal rate lookup, no matter which tier they're booked under - it wins outright, ahead of
     * any date-range rate_overrides row (legacy guests are grandfathered off seasonal pricing
     * entirely). The override amount is itself tiered by the guest's own legacyRateType - a
     * WEEKLY_5/WEEKLY_7 legacy amount is a flat total for that period and gets split across nights
     * the same way a current WEEKLY_5/WEEKLY_7 rate does, not charged in full every night.
     * Otherwise, a Regular Guest always pays the tier's base per-night amount, skipping seasonal
     * pricing entirely. For everyone else, an active rate_overrides row covering this date wins
     * over the tier's base per-night amount - surge/seasonal pricing overrides the long-stay
     * discount, not the other way around.
     */
    private BigDecimal resolveNightlyRateAmount(Integer guestId, Rate rate, LocalDate nightDate, long nightIndex) {
        Optional<Guest> guest = guestId == null ? Optional.empty() : guestRepository.findById(guestId);

        Optional<Guest> legacyGuest = guest.filter(Guest::legacyPricing)
                                           .filter(g -> g.legacyPricingAmount() != null);

        if (legacyGuest.isPresent()) {
            Guest g = legacyGuest.get();
            return tieredAmount(g.legacyPricingAmount(), g.legacyRateType(), nightIndex);
        }

        BigDecimal tieredAmount = tieredNightlyAmount(rate, nightIndex);

        if (guest.map(Guest::regularGuest).orElse(false)) {
            return tieredAmount;
        }

        return rateOverrideRepository.findActiveOverride(Rate.RateType.NIGHTLY.name(), rate.guestCount(), nightDate)
                                     .map(RateOverride::amount)
                                     .orElse(tieredAmount);
    }

    /**
     * Splits a tiered rate's flat total evenly across the nights it covers using cumulative
     * rounding - round the running total-through-this-night, then subtract the running total
     * through the previous night - rather than rounding a single per-night amount and repeating
     * it. The latter drifts away from the flat total by a few cents over several nights: a
     * $362.70 WEEKLY_7 rate divided naively is $51.81 x 7 = $362.67, three cents short of the
     * stated rate for an exact 7-night stay.
     */
    private BigDecimal tieredNightlyAmount(Rate rate, long nightIndex) {
        return tieredAmount(rate.amount(), Rate.RateType.valueOf(rate.rateType()), nightIndex);
    }

    private BigDecimal tieredAmount(BigDecimal amount, Rate.RateType rateType, long nightIndex) {
        int tierSize = switch (rateType) {
            case NIGHTLY -> 0;
            case WEEKLY_5 -> 5;
            case WEEKLY_7 -> 7;
        };

        if (tierSize == 0) {
            return amount;
        }

        BigDecimal cumulativeThroughThisNight = amount.multiply(BigDecimal.valueOf(nightIndex + 1))
                                                       .divide(BigDecimal.valueOf(tierSize), 2, RoundingMode.HALF_UP);
        BigDecimal cumulativeBeforeThisNight = amount.multiply(BigDecimal.valueOf(nightIndex))
                                                     .divide(BigDecimal.valueOf(tierSize), 2, RoundingMode.HALF_UP);

        return cumulativeThroughThisNight.subtract(cumulativeBeforeThisNight);
    }

    private BigDecimal sumNightlyRateAmounts(Integer guestId, Rate rate, LocalDate firstNight, long nights, long startIndex) {
        BigDecimal total = BigDecimal.ZERO;

        for (long i = 0; i < nights; i++) {
            total = total.add(resolveNightlyRateAmount(guestId, rate, firstNight.plusDays(i), startIndex + i));
        }

        return total;
    }

    private String resolveGuestEmail(Integer guestId) {
        if (guestId == null) {
            return null;
        }

        return guestRepository.findById(guestId)
                              .map(Guest::email)
                              .map(EncryptedString::value)
                              .orElse(null);
    }

    public String resolveGuestEmailForReservation(int reservationId) {
        return reservationRepository.findById(reservationId)
                                    .map(Reservation::guestId)
                                    .map(this::resolveGuestEmail)
                                    .orElse(null);
    }

    public String resolveGuestEmailForFolio(int folioId) {
        return reservationRepository.findByFolioId(folioId).stream()
                                    .findFirst()
                                    .map(Reservation::guestId)
                                    .map(this::resolveGuestEmail)
                                    .orElse(null);
    }

    private String generateUniqueConfirmationCode() {
        String code;

        do {
            code = ConfirmationCodeGenerator.generate();
        } while (reservationRepository.existsByConfirmationCode(code));

        return code;
    }

    /**
     * Room types unavailable for the given date range, so the frontend can disable them in the
     * room-type dropdown and surface a capacity conflict before committing to a booking - instead
     * of only at the final createReservation call, which can be deferred behind a pay-timing step
     * for future-dated/phone bookings.
     */
    public List<Integer> getUnavailableRoomTypeIds(LocalDate checkInDate, LocalDate checkOutDate, Integer excludingReservationId) {
        return roomTypeRepository.findAll().stream()
                                  .filter(roomType -> !isRoomTypeAvailable(roomType, checkInDate, checkOutDate, excludingReservationId))
                                  .map(RoomType::id)
                                  .toList();
    }

    private boolean isRoomTypeAvailable(RoomType roomType, LocalDate checkInDate, LocalDate checkOutDate, Integer excludingReservationId) {
        int overlapping = excludingReservationId != null
                ? reservationRepository.countOverlappingByRoomTypeExcludingReservation(
                        roomType.id(), checkOutDate, checkInDate, excludingReservationId)
                : reservationRepository.countOverlappingByRoomType(roomType.id(), checkOutDate, checkInDate);

        return overlapping < roomType.availableCount();
    }

    private void checkRoomTypeAvailability(RoomType roomType, LocalDate checkInDate, LocalDate checkOutDate) {
        if (!isRoomTypeAvailable(roomType, checkInDate, checkOutDate, null)) {
            throw new RoomTypeUnavailableException();
        }
    }

    private BigDecimal computeFirstNightAmount(Reservation reservation) {
        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        BigDecimal amount = resolveNightlyRateAmount(reservation.guestId(), rate, reservation.checkInDate(), 0);

        return folioService.estimateWithTax(amount);
    }


    @Transactional
    public Reservation createReservation(Reservation reservation, String roomPaymentMethodId, List<FolioService.ExtraSelection> extras) {
        LocalDateTime now = LocalDateTime.now();

        Folio folio = folioRepository.save(new Folio(0, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, now, now));

        boolean chargeRoomNow = roomPaymentMethodId != null;

        ReservationDraftResult result = createReservationOnFolio(folio, reservation, chargeRoomNow);
        Reservation savedReservation = result.reservation();
        Folio updatedFolio = result.folio();

        for (FolioService.ExtraSelection selection : Optional.ofNullable(extras).orElse(List.of())) {
            updatedFolio = folioService.addExtra(updatedFolio.id(), selection.extraId(), selection.quantity());
        }

        if (savedReservation.channel().equals(Reservation.Channel.PHONE) && chargeRoomNow) {
            paymentService.chargeFullStay(updatedFolio, updatedFolio.total(), providerFactory.getPaymentProviderName(), roomPaymentMethodId,
                    resolveGuestEmail(savedReservation.guestId()));
        }

        if (savedReservation.guestId() != null && savedReservation.channel() != Reservation.Channel.WALK_IN) {
            guestRepository.findById(savedReservation.guestId())
                           .filter(Guest::smsConsent)
                           .ifPresent(guest -> smsService.sendConfirmation(guest, savedReservation));
        }

        return savedReservation;
    }

    @Transactional
    public List<Reservation> createMultiRoomReservation(int guestId,
                                                        List<CreateMultiRoomReservationRequest.RoomLine> rooms,
                                                        LocalDate checkInDate, LocalDate checkOutDate,
                                                        Rate.RateType rateType, int guestCount,
                                                        Reservation.Channel channel, String roomPaymentMethodId) {
        if (rooms == null || rooms.isEmpty()) {
            throw new InvalidReservationException();
        }

        LocalDateTime now = LocalDateTime.now();

        Folio folio = folioRepository.save(new Folio(0, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, now, now));

        List<Reservation> savedReservations = new ArrayList<>();
        BigDecimal combinedAmount = BigDecimal.ZERO;

        for (CreateMultiRoomReservationRequest.RoomLine roomLine : rooms) {
            for (int i = 0; i < roomLine.quantity(); i++) {
                Reservation draft = new Reservation(0, 0, guestId, null, roomLine.roomTypeId(), checkInDate, checkOutDate,
                        Reservation.ReservationStatus.CONFIRMED, null, null, rateType, guestCount, channel, false, now, now, null);

                ReservationDraftResult result = createReservationOnFolio(folio, draft, true);
                folio = result.folio();
                savedReservations.add(result.reservation());
                combinedAmount = combinedAmount.add(result.estimatedStayAmount());
            }
        }

        if (channel.equals(Reservation.Channel.PHONE)) {
            paymentService.chargeFullStay(folio, combinedAmount, providerFactory.getPaymentProviderName(), roomPaymentMethodId,
                    resolveGuestEmail(guestId));
        }

        guestRepository.findById(guestId)
                       .filter(Guest::smsConsent)
                       .filter(g -> channel != Reservation.Channel.WALK_IN)
                       .ifPresent(guest -> smsService.sendConfirmation(guest, savedReservations.get(0)));

        return savedReservations;
    }

    private ReservationDraftResult createReservationOnFolio(Folio folio, Reservation reservation, boolean postFullStay) {
        LocalDateTime now = LocalDateTime.now();

        RoomType roomType = roomTypeRepository.findById(reservation.roomTypeId())
                                              .orElseThrow(RoomTypeNotFoundException::new);

        if (!reservation.checkOutDate().isAfter(reservation.checkInDate())) {
            throw new InvalidReservationException();
        }

        checkRoomTypeAvailability(roomType, reservation.checkInDate(), reservation.checkOutDate());

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long nights = getTotalNights(reservation.checkInDate(), reservation.checkOutDate());

        if (reservation.rateType() != tierForNights(nights)) {
            throw new InvalidReservationException();
        }

        String confirmationCode = generateUniqueConfirmationCode();

        Reservation savedReservation = reservationRepository.save(new Reservation(0, folio.id(), reservation.guestId(), null, roomType.id(),
                reservation.checkInDate(), reservation.checkOutDate(), reservation.status(), reservation.checkedInAt(),
                reservation.checkedOutAt(), reservation.rateType(), reservation.guestCount(), reservation.channel(), reservation.legalHold(), now, now,
                confirmationCode));

        long periodsToPost = postFullStay ? nights : 1;

        BigDecimal totalBefore = folio.total();
        Folio updatedFolio = folio;

        for (long i = 0; i < periodsToPost; i++) {
            BigDecimal periodAmount = resolveNightlyRateAmount(reservation.guestId(), rate, reservation.checkInDate().plusDays(i), i);
            updatedFolio = folioService.postCharge(updatedFolio, "GUEST ROOM", periodAmount);
        }

        BigDecimal estimatedStayAmount = updatedFolio.total().subtract(totalBefore);

        return new ReservationDraftResult(savedReservation, updatedFolio, estimatedStayAmount);
    }

    @Transactional
    public Folio settleWalkInStay(int folioId, String roomPaymentMethodId) {
        return settleWalkInStayInternal(folioId, providerFactory.getPaymentProviderName(), roomPaymentMethodId);
    }

    @Transactional
    public Folio settleWalkInStayTerminal(int folioId, Integer posDeviceId) {
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

        return settleWalkInStayInternal(folioId, providerFactory.getCardPresentProviderName(), paymentMethodToken);
    }

    private Folio settleWalkInStayInternal(int folioId, String providerName, String paymentToken) {
        Folio folio = folioRepository.findById(folioId).orElseThrow(FolioNotFoundException::new);

        List<Reservation> reservations = reservationRepository.findByFolioId(folioId);

        if (reservations.isEmpty()) {
            throw new FolioNotFoundException();
        }

        if (paymentService.isRoomPaymentSettled(folioId)) {
            throw new StayAlreadySettledException();
        }

        BigDecimal combinedAmount = reservations.stream()
                                                .map(this::estimateStayAmount)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add);

        paymentService.chargeFullStay(folio, combinedAmount, providerName, paymentToken,
                resolveGuestEmail(reservations.getFirst().guestId()));

        return folio;
    }

    private BigDecimal estimateStayAmount(Reservation reservation) {
        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        return folioService.estimateWithTax(rate.amount()
                                                .multiply(BigDecimal.valueOf(getTotalPeriods(reservation.rateType(), reservation.checkInDate(), reservation.checkOutDate()))));
    }

    public ReservationEstimateResponse estimateTotal(Rate.RateType rateType, int guestCount, LocalDate checkInDate,
                                                     LocalDate checkOutDate, Integer guestId) {
        Rate rate = rateRepository.findByRateTypeAndGuestCount(rateType, guestCount)
                                  .orElseThrow(RateNotFoundException::new);

        long totalPeriods = getTotalNights(checkInDate, checkOutDate);
        BigDecimal subtotal = sumNightlyRateAmounts(guestId, rate, checkInDate, totalPeriods, 0);
        BigDecimal total = folioService.estimateWithTax(subtotal);
        BigDecimal tax = total.subtract(subtotal);

        return new ReservationEstimateResponse(subtotal, tax, total);
    }

    public ReservationEstimateResponse estimateTotalWithExtras(Rate.RateType rateType, int guestCount, LocalDate checkInDate,
                                                               LocalDate checkOutDate, Integer guestId,
                                                               List<FolioService.ExtraSelection> extraSelections) {
        ReservationEstimateResponse roomEstimate = estimateTotal(rateType, guestCount, checkInDate, checkOutDate, guestId);

        long nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        BigDecimal extrasSubtotal = folioService.priceExtras(extraSelections, nights);

        BigDecimal subtotal = roomEstimate.subtotal().add(extrasSubtotal);
        BigDecimal total = folioService.estimateWithTax(subtotal);

        return new ReservationEstimateResponse(subtotal, total.subtract(subtotal), total);
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

        Reservation updated = new Reservation(id, existing.folioId(), reservation.guestId(), existing.roomId(), reservation.roomTypeId(), reservation.checkInDate(),
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

        return roomRepository.findAvailableOfType(reservation.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate(), id)
                             .stream()
                             .sorted(Comparator.comparingInt(Room::roomNumber))
                             .toList();
    }

    /**
     * Lets staff assign a specific room to a CONFIRMED reservation ahead of check-in (e.g. a phone
     * or future-dated walk-in booking), without any of check-in's side effects - no charge, no
     * incidentals hold, no lock passcode. Re-assignable, and the room doesn't have to match the
     * reservation's current room type - editing a booking to a different type re-points roomTypeId
     * at whichever room actually gets picked, same as moveRoom does for a CHECKED_IN guest.
     */
    @Transactional
    public Reservation assignRoom(int id, int newRoomId) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status() != Reservation.ReservationStatus.CONFIRMED) {
            throw new InvalidReservationException();
        }

        Room newRoom = roomRepository.findById(newRoomId).orElseThrow(RoomNotFoundException::new);

        boolean available = roomRepository.findAvailableOfType(newRoom.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate(), id)
                                          .stream()
                                          .anyMatch(r -> r.id() == newRoom.id());

        if (!available) {
            throw new NoRoomAvailableException();
        }

        reservationRepository.moveRoom(id, newRoom.id(), newRoom.roomTypeId());

        return reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
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

        Room room = roomRepository.findAvailableOfType(reservation.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate(), id)
                                  .stream()
                                  .filter(r -> r.id() == roomId)
                                  .findFirst()
                                  .orElseThrow(NoRoomAvailableException::new);

        reservationRepository.assignRoom(id, room.id());
        reservationRepository.updateReservationStatusToCheckedIn(id);

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long totalPeriods = getTotalNights(reservation.checkInDate(), reservation.checkOutDate());
        long alreadyPosted = folioService.countRoomChargesPosted(folio.id());
        long remainingPeriods = totalPeriods - alreadyPosted;

        for (long i = 0; i < remainingPeriods; i++) {
            BigDecimal periodAmount = resolveNightlyRateAmount(reservation.guestId(), rate,
                    reservation.checkInDate().plusDays(alreadyPosted + i), alreadyPosted + i);
            folio = folioService.postCharge(folio, "GUEST ROOM", periodAmount);
        }

        if (remainingPeriods > 0) {
            paymentService.chargeFullStay(folio, folio.total(), providerFactory.getPaymentProviderName(), roomPaymentMethodId,
                    resolveGuestEmail(reservation.guestId()));
        }

        paymentService.createIncidentalHold(folio, id, providerFactory.getPaymentProviderName(), incidentalsPaymentMethodId,
                resolveGuestEmail(reservation.guestId()));

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
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new AlreadyCheckedInException();
        } else if (!reservation.status().equals(Reservation.ReservationStatus.CONFIRMED)) {
            throw new InvalidReservationException();
        }

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

        Room room = roomRepository.findAvailableOfType(reservation.roomTypeId(), reservation.checkOutDate(), reservation.checkInDate(), id)
                                  .stream()
                                  .filter(r -> r.id() == roomId)
                                  .findFirst()
                                  .orElseThrow(NoRoomAvailableException::new);

        reservationRepository.assignRoom(id, room.id());
        reservationRepository.updateReservationStatusToCheckedIn(id);

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long totalPeriods = getTotalNights(reservation.checkInDate(), reservation.checkOutDate());
        long alreadyPosted = folioService.countRoomChargesPosted(folio.id());
        long remainingPeriods = totalPeriods - alreadyPosted;

        for (long i = 0; i < remainingPeriods; i++) {
            BigDecimal periodAmount = resolveNightlyRateAmount(reservation.guestId(), rate,
                    reservation.checkInDate().plusDays(alreadyPosted + i), alreadyPosted + i);
            folio = folioService.postCharge(folio, "GUEST ROOM", periodAmount);
        }

        if (remainingPeriods > 0) {
            paymentService.chargeFullStay(folio, folio.total(), providerFactory.getCardPresentProviderName(), paymentMethodToken,
                    resolveGuestEmail(reservation.guestId()));
        }

        paymentService.createIncidentalHold(folio, id, providerFactory.getCardPresentProviderName(), paymentMethodToken,
                resolveGuestEmail(reservation.guestId()));

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
    public Reservation payFullStayNow(int id, String roomPaymentMethodId) {
        Reservation reservation = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);

        if (!reservation.channel().equals(Reservation.Channel.WALK_IN)
                || !reservation.status().equals(Reservation.ReservationStatus.CONFIRMED)) {
            throw new InvalidReservationException();
        }

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long totalPeriods = getTotalNights(reservation.checkInDate(), reservation.checkOutDate());
        long alreadyPosted = folioService.countRoomChargesPosted(folio.id());
        long remainingPeriods = totalPeriods - alreadyPosted;

        if (remainingPeriods <= 0) {
            throw new InvalidReservationException();
        }

        for (long i = 0; i < remainingPeriods; i++) {
            BigDecimal periodAmount = resolveNightlyRateAmount(reservation.guestId(), rate,
                    reservation.checkInDate().plusDays(alreadyPosted + i), alreadyPosted + i);
            folio = folioService.postCharge(folio, "GUEST ROOM", periodAmount);
        }

        paymentService.chargeFullStay(folio, folio.total(), providerFactory.getPaymentProviderName(), roomPaymentMethodId,
                resolveGuestEmail(reservation.guestId()));

        return reservation;
    }

    @Transactional
    public Reservation payFullStayNowTerminal(int id, Integer posDeviceId) {
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

        Reservation reservation = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);

        if (!reservation.channel().equals(Reservation.Channel.WALK_IN)
                || !reservation.status().equals(Reservation.ReservationStatus.CONFIRMED)) {
            throw new InvalidReservationException();
        }

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long totalPeriods = getTotalNights(reservation.checkInDate(), reservation.checkOutDate());
        long alreadyPosted = folioService.countRoomChargesPosted(folio.id());
        long remainingPeriods = totalPeriods - alreadyPosted;

        if (remainingPeriods <= 0) {
            throw new InvalidReservationException();
        }

        for (long i = 0; i < remainingPeriods; i++) {
            BigDecimal periodAmount = resolveNightlyRateAmount(reservation.guestId(), rate,
                    reservation.checkInDate().plusDays(alreadyPosted + i), alreadyPosted + i);
            folio = folioService.postCharge(folio, "GUEST ROOM", periodAmount);
        }

        paymentService.chargeFullStay(folio, folio.total(), providerFactory.getCardPresentProviderName(), paymentMethodToken,
                resolveGuestEmail(reservation.guestId()));

        return reservation;
    }

    public CheckInEstimateResponse estimateCheckInCharge(int id) {
        Reservation reservation = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long totalPeriods = getTotalNights(reservation.checkInDate(), reservation.checkOutDate());
        long alreadyPosted = folioService.countRoomChargesPosted(folio.id());
        long remainingPeriods = totalPeriods - alreadyPosted;

        BigDecimal total = folio.total();

        if (remainingPeriods > 0) {
            BigDecimal remainingRoom = sumNightlyRateAmounts(reservation.guestId(), rate,
                    reservation.checkInDate().plusDays(alreadyPosted), remainingPeriods, alreadyPosted);

            total = total.add(folioService.estimateWithTax(remainingRoom));
        }

        return new CheckInEstimateResponse(total, remainingPeriods > 0);
    }

    @Transactional
    public Reservation backlogCheckIn(BacklogCheckInRequest request) {
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new InvalidReservationException();
        }

        Room room = roomRepository.findById(request.roomId()).orElseThrow(RoomNotFoundException::new);

        if (room.status() == Room.RoomStatus.MAINTENANCE) {
            throw new RoomUnavailableException();
        }

        boolean hasConflict = !reservationRepository.findOverlapping(room.id(), request.checkOutDate(), request.checkInDate()).isEmpty();

        if (hasConflict) {
            throw new RoomUnavailableException();
        }

        Guest guest = findOrCreateBacklogGuest(request);

        LocalDateTime now = LocalDateTime.now();

        String confirmationCode = generateUniqueConfirmationCode();

        Rate.RateType rateType = request.rateType() != null ? request.rateType() : Rate.RateType.NIGHTLY;
        int guestCount = request.guestCount() != null ? request.guestCount() : 1;

        Folio savedFolio = folioRepository.save(new Folio(0, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, now, now));

        Reservation savedReservation = reservationRepository.save(new Reservation(0, savedFolio.id(), guest.id(), room.id(), room.roomTypeId(),
                request.checkInDate(), request.checkOutDate(), Reservation.ReservationStatus.CHECKED_IN,
                request.checkInDate().atTime(STANDARD_CHECK_IN_HOUR, 0), null, rateType, guestCount,
                Reservation.Channel.WALK_IN, false, now, now, confirmationCode));

        return savedReservation;
    }

    /**
     * backlogCheckIn (and any other CHECKED_IN reservation that's fallen behind) leaves its folio
     * missing room charges - no payment is ever touched here, only the folio ledger, since backlog
     * entries already got paid some other way the system doesn't know about. Reuses the exact same
     * remaining-periods reconciliation checkIn/checkInTerminal/checkOut already do, so a reservation
     * that's already fully posted is a no-op.
     */
    @Transactional
    public SyncFoliosResult syncBacklogFolios() {
        List<String> synced = new ArrayList<>();

        List<Reservation> checkedIn = reservationRepository.findAll().stream()
                                                            .filter(r -> r.status() == Reservation.ReservationStatus.CHECKED_IN)
                                                            .toList();

        for (Reservation reservation : checkedIn) {
            Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElse(null);

            if (folio == null) {
                continue;
            }

            Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                      .orElse(null);

            if (rate == null) {
                continue;
            }

            long totalPeriods = getTotalNights(reservation.checkInDate(), reservation.checkOutDate());
            long alreadyPosted = folioService.countRoomChargesPosted(folio.id());
            long remainingPeriods = totalPeriods - alreadyPosted;

            if (remainingPeriods <= 0) {
                continue;
            }

            for (long i = 0; i < remainingPeriods; i++) {
                BigDecimal periodAmount = resolveNightlyRateAmount(reservation.guestId(), rate,
                        reservation.checkInDate().plusDays(alreadyPosted + i), alreadyPosted + i);
                folio = folioService.postCharge(folio, "GUEST ROOM", periodAmount);
            }

            synced.add(reservation.confirmationCode());
        }

        return new SyncFoliosResult(synced.size(), synced);
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
                    false, null, null, null, false, false, null, Rate.RateType.NIGHTLY, false, Guest.GuestType.INDIVIDUAL,
                    createdAt, createdAt));
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

        lockPasscodeService.revokePasscodes(id);

        Folio folio = folioRepository.findById(reservation.folioId())
                                     .orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(reservation.rateType(), reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long totalPeriods = getTotalNights(reservation.checkInDate(), reservation.checkOutDate());
        long alreadyPosted = folioService.countRoomChargesPosted(folio.id());
        long remainingPeriods = totalPeriods - alreadyPosted;

        for (long i = 0; i < remainingPeriods; i++) {
            BigDecimal periodAmount = resolveNightlyRateAmount(reservation.guestId(), rate,
                    reservation.checkInDate().plusDays(alreadyPosted + i), alreadyPosted + i);
            folio = folioService.postCharge(folio, "GUEST ROOM", periodAmount);
        }

        boolean isLastActiveReservation = !reservationRepository.existsOtherActiveByFolioId(reservation.folioId(), id);

        if (isLastActiveReservation) {
            Folio closedFolio = folioRepository.save(new Folio(folio.id(), Folio.FolioStatus.CLOSED,
                    folio.total(), folio.paidAt(), folio.createdAt(), now));

            if (!paymentService.requiresManualCapture(closedFolio)) {
                paymentService.capture(closedFolio);
                folioRepository.save(new Folio(closedFolio.id(), closedFolio.status(),
                        closedFolio.total(), LocalDateTime.now(), closedFolio.createdAt(), LocalDateTime.now()));
            }

            paymentCredentialService.scheduleExpiry(folio.id(), now.plusDays(30));
        }

        return reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
    }

    /**
     * Relocates an already-CHECKED_IN guest to a different physical room - same type or a
     * different one (e.g. a flooded room forcing a move to whatever's actually available).
     * Folio/pricing is untouched: the guest keeps what they already agreed to pay, they're just
     * physically somewhere else now. The old room's door passcode is revoked and a new one issued
     * for the new room, same as check-in does.
     */
    @Transactional
    public Reservation moveRoom(int id, int newRoomId) {
        Reservation reservation = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);

        if (reservation.status() != Reservation.ReservationStatus.CHECKED_IN) {
            throw new InvalidReservationException();
        }

        if (reservation.roomId() != null && reservation.roomId() == newRoomId) {
            throw new InvalidReservationException();
        }

        Room newRoom = roomRepository.findById(newRoomId).orElseThrow(RoomNotFoundException::new);

        boolean available = roomRepository.findAvailableOfType(newRoom.roomTypeId(), reservation.checkOutDate(), LocalDate.now(), id)
                                          .stream()
                                          .anyMatch(r -> r.id() == newRoom.id());

        if (!available) {
            throw new NoRoomAvailableException();
        }

        lockPasscodeService.revokePasscodes(id);
        reservationRepository.moveRoom(id, newRoom.id(), newRoom.roomTypeId());

        Reservation moved = reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);

        LockPasscodeService.PasscodeResult passcodeResult = lockPasscodeService.issuePasscode(moved, newRoom);

        if (passcodeResult.outcome() == LockPasscodeService.PasscodeResult.Outcome.ISSUED && moved.guestId() != null) {
            guestRepository.findById(moved.guestId())
                           .filter(Guest::smsConsent)
                           .ifPresent(guest -> smsService.sendCheckInComplete(guest, newRoom.roomNumber(), passcodeResult.passcode()));
        }

        return moved;
    }

    private record ExtendStayContext(Reservation reservation, Rate.RateType newTier, Folio folio, BigDecimal chargeAmount, LocalDateTime now) {
    }

    private void validateExtendStay(Reservation reservation, LocalDate newCheckOutDate) {
        if (!reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new InvalidReservationException();
        }

        if (!newCheckOutDate.isAfter(reservation.checkOutDate())) {
            throw new InvalidReservationException();
        }
    }

    public ReservationEstimateResponse estimateExtendStayCharge(int id, LocalDate newCheckOutDate) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        validateExtendStay(reservation, newCheckOutDate);

        long additionalNights = ChronoUnit.DAYS.between(reservation.checkOutDate(), newCheckOutDate);
        long newTotalNights = getTotalNights(reservation.checkInDate(), newCheckOutDate);
        Rate.RateType newTier = tierForNights(newTotalNights);

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);

        Rate rate = rateRepository.findByRateTypeAndGuestCount(newTier, reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        BigDecimal subtotal = sumNightlyRateAmounts(reservation.guestId(), rate, reservation.checkOutDate(), additionalNights,
                newTotalNights - additionalNights);

        for (FolioService.PerNightExtraCharge extra : folioService.distinctPerNightExtras(folio.id())) {
            subtotal = subtotal.add(extra.unitPrice()
                    .multiply(BigDecimal.valueOf(extra.quantity()))
                    .multiply(BigDecimal.valueOf(additionalNights)));
        }

        BigDecimal total = folioService.estimateWithTax(subtotal);

        return new ReservationEstimateResponse(subtotal, total.subtract(subtotal), total);
    }

    private ExtendStayContext prepareExtendStay(int id, LocalDate newCheckOutDate) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        validateExtendStay(reservation, newCheckOutDate);

        boolean hasConflict = reservationRepository.findOverlapping(reservation.roomId(), newCheckOutDate, reservation.checkOutDate())
                                                    .stream()
                                                    .anyMatch(r -> r.id() != id);

        if (hasConflict) {
            throw new DateConflictException();
        }

        boolean isRegularGuest = reservation.guestId() != null && guestRepository.findById(reservation.guestId())
                                                                                  .map(Guest::regularGuest)
                                                                                  .orElse(false);

        // findOverlapping above only catches a conflict against a room already assigned to another
        // reservation. A not-yet-checked-in reservation for the same room type has room_id = NULL
        // (deferred room assignment) until its own check-in, so it's invisible to that check -
        // extending into its dates would otherwise go through with nothing stopping it. Regular
        // Guests are exempted: a long-term guest already in the room takes priority over a newer,
        // not-yet-arrived reservation for the same room type.
        if (!isRegularGuest) {
            RoomType roomType = roomTypeRepository.findById(reservation.roomTypeId())
                                                  .orElseThrow(RoomTypeNotFoundException::new);

            int overlappingByType = reservationRepository.countOverlappingByRoomTypeExcludingReservation(
                    roomType.id(), newCheckOutDate, reservation.checkOutDate(), id);

            if (overlappingByType >= roomType.availableCount()) {
                throw new RoomTypeUnavailableException();
            }
        }

        long additionalNights = ChronoUnit.DAYS.between(reservation.checkOutDate(), newCheckOutDate);
        long newTotalNights = getTotalNights(reservation.checkInDate(), newCheckOutDate);
        Rate.RateType newTier = tierForNights(newTotalNights);

        Folio folio = folioRepository.getFolioByReservationId(reservation.id()).orElseThrow(FolioNotFoundException::new);
        BigDecimal folioTotalBefore = folio.total();

        Rate rate = rateRepository.findByRateTypeAndGuestCount(newTier, reservation.guestCount())
                                  .orElseThrow(RateNotFoundException::new);

        long originalNights = newTotalNights - additionalNights;

        for (long i = 0; i < additionalNights; i++) {
            BigDecimal periodAmount = resolveNightlyRateAmount(reservation.guestId(), rate, reservation.checkOutDate().plusDays(i), originalNights + i);
            folio = folioService.postCharge(folio, "GUEST ROOM", periodAmount);
        }

        for (FolioService.PerNightExtraCharge extra : folioService.distinctPerNightExtras(folio.id())) {
            BigDecimal extraAmount = extra.unitPrice().multiply(BigDecimal.valueOf(extra.quantity()));
            String description = extra.quantity() > 1 ? extra.extraName() + " X" + extra.quantity() : extra.extraName();

            for (long i = 0; i < additionalNights; i++) {
                folio = folioService.postCharge(folio, description.toUpperCase(), extraAmount, extra.extraId(), extra.quantity());
            }
        }

        BigDecimal chargeAmount = folio.total().subtract(folioTotalBefore);

        return new ExtendStayContext(reservation, newTier, folio, chargeAmount, LocalDateTime.now());
    }

    private Reservation saveExtendedReservation(ExtendStayContext ctx, LocalDate newCheckOutDate) {
        Reservation reservation = ctx.reservation();

        Reservation extended = new Reservation(reservation.id(), reservation.folioId(), reservation.guestId(), reservation.roomId(), reservation.roomTypeId(),
                reservation.checkInDate(), newCheckOutDate, reservation.status(), reservation.checkedInAt(), reservation.checkedOutAt(),
                ctx.newTier(), reservation.guestCount(), reservation.channel(), reservation.legalHold(),
                reservation.createdAt(), ctx.now(), reservation.confirmationCode());

        return reservationRepository.save(extended);
    }

    @Transactional
    public ExtendStayResult extendStay(int id, LocalDate newCheckOutDate) {
        ExtendStayContext ctx = prepareExtendStay(id, newCheckOutDate);

        ReusablePaymentCredential credential = reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(ctx.folio().id())
                .stream()
                .filter(c -> c.expiresAt() == null || c.expiresAt().isAfter(ctx.now()))
                .filter(c -> !ProviderFactory.CARD_PRESENT_RECORD_ONLY_PROVIDER.equals(c.provider()))
                .findFirst()
                .orElseThrow(NoReusableCredentialException::new);

        paymentService.chargeStoredCredential(ctx.folio(), credential, ctx.chargeAmount(), "Stay extension to " + newCheckOutDate,
                resolveGuestEmail(ctx.reservation().guestId()));

        Reservation saved = saveExtendedReservation(ctx, newCheckOutDate);

        return new ExtendStayResult(saved, ctx.chargeAmount());
    }

    @Transactional
    public ExtendStayResult extendStayTerminal(int id, LocalDate newCheckOutDate, Integer posDeviceId) {
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

        ExtendStayContext ctx = prepareExtendStay(id, newCheckOutDate);

        paymentService.chargeCardPresent(ctx.folio(), ctx.chargeAmount(), providerFactory.getCardPresentProviderName(),
                paymentMethodToken, "Stay extension to " + newCheckOutDate, resolveGuestEmail(ctx.reservation().guestId()));

        Reservation saved = saveExtendedReservation(ctx, newCheckOutDate);

        return new ExtendStayResult(saved, ctx.chargeAmount());
    }

    @Transactional
    public Reservation cancelReservation(int id) {
        Reservation reservation = reservationRepository.findById(id)
                                                       .orElseThrow(ReservationNotFoundException::new);

        if (reservation.status().equals(Reservation.ReservationStatus.CHECKED_OUT) || reservation.status().equals(Reservation.ReservationStatus.CHECKED_IN)) {
            throw new CannotCancelException();
        }

        boolean isLastActiveReservation = !reservationRepository.existsOtherActiveByFolioId(reservation.folioId(), id);

        folioRepository.findById(reservation.folioId())
                       .ifPresent(f -> {
                           if (isLastActiveReservation) {
                               paymentService.cancelOpenHolds(f);
                               folioRepository.closeFolio(f.id());
                           } else {
                               paymentService.refundReservationShare(f, estimateStayAmount(reservation), BigDecimal.ZERO);
                           }
                       });

        return reservationRepository.save(new Reservation(id, reservation.folioId(), reservation.guestId(), reservation.roomId(), reservation.roomTypeId(),
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
        boolean isLastActiveReservation = !reservationRepository.existsOtherActiveByFolioId(reservation.folioId(), id);

        folioRepository.findById(reservation.folioId())
                       .ifPresent(f -> {
                           paymentService.refundReservationShare(f, estimateStayAmount(reservation), firstNightAmount);

                           if (isLastActiveReservation) {
                               folioRepository.closeFolio(f.id());
                           }
                       });

        return reservationRepository.save(new Reservation(id, reservation.folioId(), reservation.guestId(), reservation.roomId(), reservation.roomTypeId(),
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

    private record ReservationDraftResult(Reservation reservation, Folio folio, BigDecimal estimatedStayAmount) {
    }
}
