package com.staydesk.service

import com.staydesk.exception.CardPresentRecordOnlyDisabledException
import com.staydesk.exception.DateConflictException
import com.staydesk.exception.InvalidReservationException
import com.staydesk.exception.NoReusableCredentialException
import com.staydesk.exception.PosDeviceNotFoundException
import com.staydesk.exception.RoomNotFoundException
import com.staydesk.exception.RoomUnavailableException
import com.staydesk.model.EncryptedString
import com.staydesk.model.Folio
import com.staydesk.model.Guest
import com.staydesk.model.PosDevice
import com.staydesk.model.Rate
import com.staydesk.model.RateOverride
import com.staydesk.model.Reservation
import com.staydesk.model.ReusablePaymentCredential
import com.staydesk.model.Room
import com.staydesk.model.RoomType
import com.staydesk.model.request.BacklogCheckInRequest
import com.staydesk.provider.ProviderFactory
import com.staydesk.repository.*
import com.staydesk.security.PiiCipher
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.LocalDate
import java.time.LocalDateTime

class ReservationServiceSpec extends Specification {

    ReservationRepository reservationRepository = Mock()
    RoomRepository roomRepository = Mock()
    RoomTypeRepository roomTypeRepository = Mock()
    FolioRepository folioRepository = Mock()
    RateRepository rateRepository = Mock()
    RateOverrideRepository rateOverrideRepository = Mock()
    PaymentService paymentService = Mock()
    FolioService folioService = Mock()
    GuestRepository guestRepository = Mock()
    SmsService smsService = Mock()
    LockPasscodeService lockPasscodeService = Mock()
    ProviderFactory providerFactory = Mock()
    PosDeviceRepository posDeviceRepository = Mock()
    PaymentCredentialService paymentCredentialService = Mock()
    PiiCipher piiCipher = Mock()
    ReusablePaymentCredentialRepository reusablePaymentCredentialRepository = Mock()

    @Subject
    ReservationService reservationService = new ReservationService(reservationRepository, roomRepository, roomTypeRepository,
            folioRepository, rateRepository, rateOverrideRepository, paymentService, folioService, guestRepository, smsService,
            lockPasscodeService, providerFactory, posDeviceRepository, paymentCredentialService, piiCipher,
            reusablePaymentCredentialRepository)

    private static Reservation reservation(Reservation.ReservationStatus status, Reservation.Channel channel,
                                           Rate.RateType rateType = Rate.RateType.NIGHTLY) {
        new Reservation(1, 7, 3, 2, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 13),
                status, null, null, rateType, 1, channel, false, LocalDateTime.now(), LocalDateTime.now(), "123456")
    }

    def "marks a CONFIRMED PHONE reservation as NO_SHOW, refunds all but first night, and closes the folio"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE)
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.markNoShow(1)

        then:
        1 * paymentService.refundAllButFirstNight(folio, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 })
        1 * folioRepository.closeFolio(9)
        result.status() == Reservation.ReservationStatus.NO_SHOW
        result.roomId() == res.roomId()
    }

    def "throws InvalidReservationException for a WALK_IN reservation"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN)
        reservationRepository.findById(1) >> Optional.of(res)

        when:
        reservationService.markNoShow(1)

        then:
        thrown(InvalidReservationException)
        0 * paymentService.refundAllButFirstNight(_, _)
        0 * folioRepository.closeFolio(_)
    }

    @Unroll
    def "throws InvalidReservationException when status is #status"() {
        given:
        reservationRepository.findById(1) >> Optional.of(reservation(status, Reservation.Channel.PHONE))

        when:
        reservationService.markNoShow(1)

        then:
        thrown(InvalidReservationException)

        where:
        status << [Reservation.ReservationStatus.CHECKED_IN, Reservation.ReservationStatus.CHECKED_OUT,
                   Reservation.ReservationStatus.CANCELLED, Reservation.ReservationStatus.NO_SHOW]
    }

    def "transitions status without throwing when the reservation has no folio"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE)
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        folioRepository.getFolioByReservationId(1) >> Optional.empty()
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.markNoShow(1)

        then:
        0 * paymentService.refundAllButFirstNight(_, _)
        0 * folioRepository.closeFolio(_)
        result.status() == Reservation.ReservationStatus.NO_SHOW
    }

    @Unroll
    def "computes the pre-tax first-night base amount correctly for #rateType"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE, rateType)
        def rate = new Rate(1, rateType.name(), 1, rateAmount, LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(500), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(rateType, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        guestRepository.findById(7) >> Optional.empty()
        reservationRepository.save(_) >> { Reservation r -> r }
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }   // identity: isolates division math from tax logic

        when:
        reservationService.markNoShow(1)

        then:
        1 * paymentService.refundAllButFirstNight(folio, { BigDecimal amt -> amt.compareTo(expectedBase) == 0 })

        where:
        rateType               | rateAmount              || expectedBase
        Rate.RateType.NIGHTLY  | BigDecimal.valueOf(80)  || BigDecimal.valueOf(80)
        Rate.RateType.WEEKLY_5 | BigDecimal.valueOf(350) || BigDecimal.valueOf(70.00)
        Rate.RateType.WEEKLY_7 | BigDecimal.valueOf(490) || BigDecimal.valueOf(70.00)
    }

    def "checkOut schedules a 30-day credential expiry after closing the folio"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.PHONE)
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        guestRepository.findById(7) >> Optional.empty()
        folioService.postCharge(_, _, _) >> folio

        when:
        reservationService.checkOut(1)

        then:
        1 * paymentCredentialService.scheduleExpiry(9, { LocalDateTime expiry ->
            expiry.isAfter(LocalDateTime.now().plusDays(29)) && expiry.isBefore(LocalDateTime.now().plusDays(31))
        })
    }

    def "checkOut posts no additional room nights when they were already posted at check-in"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        guestRepository.findById(7) >> Optional.empty()
        folioService.countRoomChargesPosted(9) >> 3

        when:
        reservationService.checkOut(1)

        then:
        0 * folioService.postCharge(*_)
    }

    private static BacklogCheckInRequest backlogRequest(String email = null, String phoneNumber = null) {
        new BacklogCheckInRequest(5, "James", "Reece", email, phoneNumber,
                LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 28), null, null)
    }

    def "backlogCheckIn creates a placeholder guest, then a CHECKED_IN reservation and its folio, with no payment activity"() {
        given:
        def room = new Room(5, 26, 2, Room.RoomStatus.AVAILABLE, null, null, LocalDateTime.now(), LocalDateTime.now())
        def savedGuest = new Guest(9, new EncryptedString("James"), new EncryptedString("Reece"),
                new EncryptedString("backlog@placeholder"), "hashed-placeholder-email", new EncryptedString("0000000000"),
                false, false, null, null, null, false, false, null, LocalDateTime.now(), LocalDateTime.now())
        def savedReservation = new Reservation(11, 9, 5, 2, LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 28),
                Reservation.ReservationStatus.CHECKED_IN, LocalDate.of(2026, 8, 21).atTime(15, 0), null,
                Rate.RateType.NIGHTLY, 1, Reservation.Channel.WALK_IN, false, LocalDateTime.now(), LocalDateTime.now(), "123456")

        roomRepository.findById(5) >> Optional.of(room)
        piiCipher.hash(_) >> "hashed-placeholder-email"
        guestRepository.findByEmailHash("hashed-placeholder-email") >> Optional.empty()
        reservationRepository.existsByConfirmationCode(_) >> false
        reservationRepository.findOverlapping(5, _, _) >> []

        when:
        def result = reservationService.backlogCheckIn(backlogRequest())

        then:
        1 * guestRepository.save({ Guest g ->
            g.firstName().value() == "James" && g.lastName().value() == "Reece" &&
                    g.phoneNumber().value() == "0000000000" && !g.smsConsent()
        }) >> savedGuest
        1 * reservationRepository.save({ Reservation r ->
            r.guestId() == 9 && r.roomId() == 5 && r.roomTypeId() == 2 &&
                    r.status() == Reservation.ReservationStatus.CHECKED_IN && r.checkedInAt() != null &&
                    r.checkedOutAt() == null && r.rateType() == Rate.RateType.NIGHTLY && r.guestCount() == 1 &&
                    r.channel() == Reservation.Channel.WALK_IN
        }) >> savedReservation
        1 * folioRepository.save({ Folio f ->
            f.reservationId() == 11 && f.status() == Folio.FolioStatus.OPEN && f.total().compareTo(BigDecimal.ZERO) == 0
        })
        0 * paymentService._
        result.status() == Reservation.ReservationStatus.CHECKED_IN
    }

    def "backlogCheckIn reuses an existing guest found by email hash instead of creating a new one"() {
        given:
        def room = new Room(5, 26, 2, Room.RoomStatus.AVAILABLE, null, null, LocalDateTime.now(), LocalDateTime.now())
        def existingGuest = new Guest(3, new EncryptedString("James"), new EncryptedString("Reece"),
                new EncryptedString("james@example.com"), "hashed-real-email", new EncryptedString("5551234567"),
                true, false, null, null, null, false, false, null, LocalDateTime.now(), LocalDateTime.now())

        roomRepository.findById(5) >> Optional.of(room)
        piiCipher.hash("james@example.com") >> "hashed-real-email"
        guestRepository.findByEmailHash("hashed-real-email") >> Optional.of(existingGuest)
        reservationRepository.existsByConfirmationCode(_) >> false
        reservationRepository.findOverlapping(5, _, _) >> []
        reservationRepository.save(_) >> { Reservation r -> r }
        folioRepository.save(_) >> { Folio f -> f }

        when:
        def result = reservationService.backlogCheckIn(backlogRequest("James@Example.com"))

        then:
        0 * guestRepository.save(_)
        result.guestId() == 3
    }

    def "backlogCheckIn throws RoomNotFoundException when the room doesn't exist"() {
        given:
        roomRepository.findById(5) >> Optional.empty()

        when:
        reservationService.backlogCheckIn(backlogRequest())

        then:
        thrown(RoomNotFoundException)
        0 * guestRepository.save(_)
        0 * reservationRepository.save(_)
    }

    def "backlogCheckIn throws RoomUnavailableException when the room is in MAINTENANCE"() {
        given:
        def room = new Room(5, 26, 2, Room.RoomStatus.MAINTENANCE, null, null, LocalDateTime.now(), LocalDateTime.now())
        roomRepository.findById(5) >> Optional.of(room)

        when:
        reservationService.backlogCheckIn(backlogRequest())

        then:
        thrown(RoomUnavailableException)
        0 * guestRepository.save(_)
        0 * reservationRepository.save(_)
    }

    def "backlogCheckIn throws RoomUnavailableException when the room has an overlapping reservation for those dates"() {
        given:
        def room = new Room(5, 26, 2, Room.RoomStatus.AVAILABLE, null, null, LocalDateTime.now(), LocalDateTime.now())
        def conflicting = new Reservation(9, 3, 5, 2, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 25),
                Reservation.ReservationStatus.CHECKED_IN, null, null, Rate.RateType.NIGHTLY, 1, Reservation.Channel.WALK_IN,
                false, LocalDateTime.now(), LocalDateTime.now(), "111222")
        roomRepository.findById(5) >> Optional.of(room)
        reservationRepository.findOverlapping(5, LocalDate.of(2026, 8, 28), LocalDate.of(2026, 8, 21)) >> [conflicting]

        when:
        reservationService.backlogCheckIn(backlogRequest())

        then:
        thrown(RoomUnavailableException)
        0 * guestRepository.save(_)
        0 * reservationRepository.save(_)
    }

    def "backlogCheckIn throws InvalidReservationException when checkOutDate is not after checkInDate"() {
        given:
        def request = new BacklogCheckInRequest(5, "James", "Reece", null, null,
                LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 21), null, null)

        when:
        reservationService.backlogCheckIn(request)

        then:
        thrown(InvalidReservationException)
        0 * roomRepository.findById(_)
    }

    def "syncBacklogFolios posts the missing GUEST ROOM charges for a CHECKED_IN reservation with an empty folio"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findAll() >> [res]
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.countRoomChargesPosted(9) >> 0
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }

        when:
        def result = reservationService.syncBacklogFolios()

        then:
        // reservation() spans 2026-07-10 -> 2026-07-13: 3 nights
        3 * folioService.postCharge(_, "GUEST ROOM", _)
        0 * paymentService._
        result.syncedCount() == 1
        result.confirmationCodes() == ["123456"]
    }

    def "syncBacklogFolios skips a reservation whose folio is already fully posted"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findAll() >> [res]
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        folioService.countRoomChargesPosted(9) >> 3

        when:
        def result = reservationService.syncBacklogFolios()

        then:
        0 * folioService.postCharge(*_)
        result.syncedCount() == 0
        result.confirmationCodes() == []
    }

    def "syncBacklogFolios ignores reservations that aren't CHECKED_IN"() {
        given:
        reservationRepository.findAll() >> [reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE)]

        when:
        def result = reservationService.syncBacklogFolios()

        then:
        0 * folioRepository.getFolioByReservationId(_)
        0 * folioService.postCharge(*_)
        result.syncedCount() == 0
    }

    private static ReusablePaymentCredential credential(LocalDateTime expiresAt = null) {
        new ReusablePaymentCredential(4, 9, 1, "authorizenet", "cust-1", "tok-1", "4242",
                false, null, expiresAt, LocalDateTime.now(), LocalDateTime.now())
    }

    def "extendStay charges the stored credential for the added periods and updates checkOutDate"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.WEEKLY_7)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "WEEKLY_7", 1, BigDecimal.valueOf(350), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.findOverlapping(3, LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 13)) >> []
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.WEEKLY_7, 1) >> Optional.of(rate)
        guestRepository.findById(7) >> Optional.empty()
        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(9) >> [credential()]
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(350)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        folioService.distinctPerNightExtras(9) >> []
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.extendStay(1, LocalDate.of(2026, 7, 17))

        then:
        1 * paymentService.chargeStoredCredential({ it.id() == 9 }, { it.id() == 4 }, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(350)) == 0 }, _, _)
        result.reservation().checkOutDate() == LocalDate.of(2026, 7, 17)
        result.reservation().checkInDate() == LocalDate.of(2026, 7, 10)
        result.amountCharged().compareTo(BigDecimal.valueOf(350)) == 0
    }

    def "extendStay also tops up an existing PER_NIGHT extra for just the added nights"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.findOverlapping(3, LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 13)) >> []
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(9) >> [credential()]
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        folioService.distinctPerNightExtras(9) >> [new FolioService.PerNightExtraCharge(2, "Pet Fee", BigDecimal.valueOf(25), 1)]
        folioService.postCharge(_, "PET FEE", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(25)) == 0 }, 2, 1) >>
                { Folio f, String d, BigDecimal amt, Integer extraId, Integer qty -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.extendStay(1, LocalDate.of(2026, 7, 16))

        then:
        1 * paymentService.chargeStoredCredential({ it.id() == 9 }, _, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(315)) == 0 }, _, _)
        result.amountCharged().compareTo(BigDecimal.valueOf(315)) == 0
    }

    def "estimateExtendStayCharge includes both the added room nights and any PER_NIGHT extras, without charging or posting anything"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.distinctPerNightExtras(9) >> [new FolioService.PerNightExtraCharge(2, "Pet Fee", BigDecimal.valueOf(25), 1)]
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }

        when:
        def result = reservationService.estimateExtendStayCharge(1, LocalDate.of(2026, 7, 16))

        then:
        0 * folioService.postCharge(*_)
        0 * paymentService.chargeStoredCredential(*_)
        0 * reservationRepository.save(_)
        result.total().compareTo(BigDecimal.valueOf(315)) == 0
    }

    def "estimateExtendStayCharge throws InvalidReservationException when the reservation isn't CHECKED_IN"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN)
        reservationRepository.findById(1) >> Optional.of(res)

        when:
        reservationService.estimateExtendStayCharge(1, LocalDate.of(2026, 7, 20))

        then:
        thrown(InvalidReservationException)
    }

    def "extendStay throws InvalidReservationException when the reservation isn't CHECKED_IN"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN)
        reservationRepository.findById(1) >> Optional.of(res)

        when:
        reservationService.extendStay(1, LocalDate.of(2026, 7, 20))

        then:
        thrown(InvalidReservationException)
        0 * reservationRepository.save(_)
        0 * paymentService.chargeStoredCredential(*_)
    }

    def "extendStay throws InvalidReservationException when the new checkout isn't after the current one"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN)
        reservationRepository.findById(1) >> Optional.of(res)

        when:
        reservationService.extendStay(1, LocalDate.of(2026, 7, 13))

        then:
        thrown(InvalidReservationException)
        0 * reservationRepository.save(_)
        0 * paymentService.chargeStoredCredential(*_)
    }

    def "extendStay throws InvalidReservationException when the extended length doesn't land on a WEEKLY_7 boundary"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.WEEKLY_7)
        reservationRepository.findById(1) >> Optional.of(res)

        when:
        reservationService.extendStay(1, LocalDate.of(2026, 7, 16))

        then:
        thrown(InvalidReservationException)
        0 * reservationRepository.save(_)
        0 * paymentService.chargeStoredCredential(*_)
    }

    def "extendStay allows any extension length for NIGHTLY reservations and charges per added night"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.findOverlapping(3, LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 13)) >> []
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(9) >> [credential()]
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        folioService.distinctPerNightExtras(9) >> []
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.extendStay(1, LocalDate.of(2026, 7, 16))

        then:
        1 * paymentService.chargeStoredCredential({ it.id() == 9 }, _, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(240)) == 0 }, _, _)
        result.reservation().checkOutDate() == LocalDate.of(2026, 7, 16)
        result.amountCharged().compareTo(BigDecimal.valueOf(240)) == 0
    }

    def "extendStay throws DateConflictException when another reservation occupies the room during the extension window"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def conflicting = new Reservation(2, 8, 4, 2, LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 15),
                Reservation.ReservationStatus.CONFIRMED, null, null, Rate.RateType.NIGHTLY, 1, Reservation.Channel.WALK_IN,
                false, LocalDateTime.now(), LocalDateTime.now(), "654321")
        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.findOverlapping(3, LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 13)) >> [conflicting]

        when:
        reservationService.extendStay(1, LocalDate.of(2026, 7, 16))

        then:
        thrown(DateConflictException)
        0 * reservationRepository.save(_)
        0 * paymentService.chargeStoredCredential(*_)
    }

    def "extendStay throws NoReusableCredentialException when there's no active credential on file"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.findOverlapping(3, LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 13)) >> []
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        reusablePaymentCredentialRepository.findByFolioIdAndRevokedFalse(9) >> []
        folioService.postCharge(_, "GUEST ROOM", _) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        folioService.distinctPerNightExtras(9) >> []

        when:
        reservationService.extendStay(1, LocalDate.of(2026, 7, 16))

        then:
        thrown(NoReusableCredentialException)
        0 * reservationRepository.save(_)
        0 * paymentService.chargeStoredCredential(*_)
    }

    def "extendStayTerminal charges the paired POS device for the added periods and updates checkOutDate"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def device = new PosDevice(6, "dev-token-1", "Front Desk", null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now())

        posDeviceRepository.findById(6) >> Optional.of(device)
        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.findOverlapping(3, LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 13)) >> []
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        providerFactory.getCardPresentProviderName() >> "elavon_cpi"
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        folioService.distinctPerNightExtras(9) >> []
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.extendStayTerminal(1, LocalDate.of(2026, 7, 16), 6)

        then:
        1 * paymentService.chargeCardPresent({ it.id() == 9 }, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(240)) == 0 }, "elavon_cpi", "dev-token-1", _, _)
        result.reservation().checkOutDate() == LocalDate.of(2026, 7, 16)
        result.amountCharged().compareTo(BigDecimal.valueOf(240)) == 0
    }

    def "extendStayTerminal throws PosDeviceNotFoundException when the given device doesn't resolve"() {
        given:
        posDeviceRepository.findById(6) >> Optional.empty()

        when:
        reservationService.extendStayTerminal(1, LocalDate.of(2026, 7, 16), 6)

        then:
        thrown(PosDeviceNotFoundException)
        0 * reservationRepository.findById(_)
        0 * paymentService.chargeCardPresent(*_)
    }

    def "extendStayTerminal falls back to record-only charging when no device is given and record-only is enabled"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        providerFactory.isCardPresentRecordOnly() >> true
        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.findOverlapping(3, LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 13)) >> []
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        providerFactory.getCardPresentProviderName() >> "elavon_cpi_manual"
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        folioService.distinctPerNightExtras(9) >> []
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.extendStayTerminal(1, LocalDate.of(2026, 7, 16), null)

        then:
        1 * paymentService.chargeCardPresent({ it.id() == 9 }, _, "elavon_cpi_manual", "no-device-record-only", _, _)
        result.reservation().checkOutDate() == LocalDate.of(2026, 7, 16)
        0 * posDeviceRepository.findById(_)
    }

    def "extendStayTerminal throws CardPresentRecordOnlyDisabledException when no device is given and record-only is disabled"() {
        given:
        providerFactory.isCardPresentRecordOnly() >> false

        when:
        reservationService.extendStayTerminal(1, LocalDate.of(2026, 7, 16), null)

        then:
        thrown(CardPresentRecordOnlyDisabledException)
        0 * reservationRepository.findById(_)
        0 * paymentService.chargeCardPresent(*_)
    }

    private static Room availableRoom() {
        new Room(5, 101, 2, Room.RoomStatus.AVAILABLE, null, null, LocalDateTime.now(), LocalDateTime.now())
    }

    def "checkIn charges the folio's real total, including any already-posted extras, for a WALK_IN reservation"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def room = availableRoom()
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(155), null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        roomRepository.findAvailableOfType(2, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 10)) >> [room]
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.countRoomChargesPosted(9) >> 1
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        lockPasscodeService.issuePasscode(_, room) >> new LockPasscodeService.PasscodeResult(LockPasscodeService.PasscodeResult.Outcome.NO_LOCK_ASSIGNED, null)

        when:
        reservationService.checkIn(1, 5, "cred-1", "token-1")

        then:
        1 * paymentService.chargeFullStay({ it.id() == 9 }, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(315)) == 0 }, _, "token-1", _)
    }

    def "checkInTerminal charges the folio's real total, including any already-posted extras, for a WALK_IN reservation"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def room = availableRoom()
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(155), null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def device = new PosDevice(6, "dev-token-1", "Front Desk", null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now())

        posDeviceRepository.findById(6) >> Optional.of(device)
        reservationRepository.findById(1) >> Optional.of(res)
        roomRepository.findAvailableOfType(2, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 10)) >> [room]
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.countRoomChargesPosted(9) >> 1
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        lockPasscodeService.issuePasscode(_, room) >> new LockPasscodeService.PasscodeResult(LockPasscodeService.PasscodeResult.Outcome.NO_LOCK_ASSIGNED, null)

        when:
        reservationService.checkInTerminal(1, 5, 6)

        then:
        1 * paymentService.chargeFullStay({ it.id() == 9 }, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(315)) == 0 }, _, "dev-token-1", _)
    }

    def "estimateCheckInCharge combines the folio's current total with the remaining room nights for a WALK_IN reservation"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN, Rate.RateType.NIGHTLY)
        def folio = new Folio(9, res.id(), Folio.FolioStatus.OPEN, BigDecimal.valueOf(155), null, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.countRoomChargesPosted(9) >> 1
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }

        when:
        def result = reservationService.estimateCheckInCharge(1)

        then:
        result.total().compareTo(BigDecimal.valueOf(315)) == 0
    }

    private static Guest legacyPricedGuest(BigDecimal legacyAmount = BigDecimal.valueOf(50)) {
        new Guest(7, new EncryptedString("James"), new EncryptedString("Reece"), new EncryptedString("james@example.com"),
                "hash", new EncryptedString("5551234567"), false, false, null, null, null, false,
                true, legacyAmount, LocalDateTime.now(), LocalDateTime.now())
    }

    def "createReservation charges the guest's legacy price instead of the standard rate"() {
        given:
        def draft = new Reservation(0, 7, null, 2, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3),
                Reservation.ReservationStatus.CONFIRMED, null, null, Rate.RateType.NIGHTLY, 1, Reservation.Channel.PHONE,
                false, LocalDateTime.now(), LocalDateTime.now(), null)
        def roomType = new RoomType(2, "QUEEN", 5, 0, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def savedFolio = new Folio(9, 0, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())

        roomTypeRepository.findById(2) >> Optional.of(roomType)
        reservationRepository.countOverlappingByRoomType(2, _, _) >> 0
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        reservationRepository.existsByConfirmationCode(_) >> false
        reservationRepository.save(_) >> { Reservation r -> r }
        folioRepository.save(_) >> savedFolio
        guestRepository.findById(7) >> Optional.of(legacyPricedGuest())
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(50)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }

        when:
        reservationService.createReservation(draft, "token-1", [])

        then:
        1 * paymentService.chargeFullStay({ it.id() == 9 }, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(100)) == 0 }, _, "token-1",
                "james@example.com")
    }

    def "createReservation passes null customerEmail when the guest has none on file"() {
        given:
        def draft = new Reservation(0, 7, null, 2, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3),
                Reservation.ReservationStatus.CONFIRMED, null, null, Rate.RateType.NIGHTLY, 1, Reservation.Channel.PHONE,
                false, LocalDateTime.now(), LocalDateTime.now(), null)
        def roomType = new RoomType(2, "QUEEN", 5, 0, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def savedFolio = new Folio(9, 0, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())

        roomTypeRepository.findById(2) >> Optional.of(roomType)
        reservationRepository.countOverlappingByRoomType(2, _, _) >> 0
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        reservationRepository.existsByConfirmationCode(_) >> false
        reservationRepository.save(_) >> { Reservation r -> r }
        folioRepository.save(_) >> savedFolio
        guestRepository.findById(7) >> Optional.empty()
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }

        when:
        reservationService.createReservation(draft, "token-1", [])

        then:
        1 * paymentService.chargeFullStay({ it.id() == 9 }, _, _, "token-1", null)
    }

    def "createReservation posts staged extras before charging, so a PHONE booking's full-stay charge includes them"() {
        given:
        def draft = new Reservation(0, 7, null, 2, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3),
                Reservation.ReservationStatus.CONFIRMED, null, null, Rate.RateType.NIGHTLY, 1, Reservation.Channel.PHONE,
                false, LocalDateTime.now(), LocalDateTime.now(), null)
        def roomType = new RoomType(2, "QUEEN", 5, 0, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def savedFolio = new Folio(9, 0, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())

        roomTypeRepository.findById(2) >> Optional.of(roomType)
        reservationRepository.countOverlappingByRoomType(2, _, _) >> 0
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        reservationRepository.existsByConfirmationCode(_) >> false
        reservationRepository.save(_) >> { Reservation r -> r }
        folioRepository.save(_) >> savedFolio
        guestRepository.findById(7) >> Optional.empty()
        folioService.postCharge(_, "GUEST ROOM", { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(80)) == 0 }) >>
                { Folio f, String d, BigDecimal amt -> new Folio(f.id(), f.reservationId(), f.status(), f.total().add(amt), f.paidAt(), f.createdAt(), LocalDateTime.now()) }
        folioService.addExtra(_, _, _) >>
                { Integer folioId, Integer extraId, Integer quantity -> new Folio(folioId, 0, Folio.FolioStatus.OPEN, BigDecimal.valueOf(185), null, LocalDateTime.now(), LocalDateTime.now()) }

        when:
        reservationService.createReservation(draft, "token-1", [new FolioService.ExtraSelection(2, 1)])

        then:
        1 * folioService.addExtra(9, 2, 1) >>
                new Folio(9, 0, Folio.FolioStatus.OPEN, BigDecimal.valueOf(185), null, LocalDateTime.now(), LocalDateTime.now())
        1 * paymentService.chargeFullStay({ it.id() == 9 }, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(185)) == 0 }, _, "token-1", _)
    }

    def "estimateTotal uses the guest's legacy price when legacy pricing is enabled"() {
        given:
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        guestRepository.findById(7) >> Optional.of(legacyPricedGuest())
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }

        when:
        def result = reservationService.estimateTotal(Rate.RateType.NIGHTLY, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 7)

        then:
        result.subtotal().compareTo(BigDecimal.valueOf(100)) == 0
    }

    def "estimateTotal uses the standard rate when no guestId is given"() {
        given:
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }

        when:
        def result = reservationService.estimateTotal(Rate.RateType.NIGHTLY, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), null)

        then:
        0 * guestRepository.findById(_)
        result.subtotal().compareTo(BigDecimal.valueOf(160)) == 0
    }

    def "estimateTotal charges an active NIGHTLY rate override for the night it covers, and the base rate for the other nights"() {
        given:
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        guestRepository.findById(7) >> Optional.empty()
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }

        // end_date is exclusive: Aug 2 -> Aug 3 covers exactly the Aug 2 night
        def override = new RateOverride(3, "NIGHTLY", 1, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3),
                BigDecimal.valueOf(200), "One-night surge", LocalDateTime.now(), LocalDateTime.now())
        rateOverrideRepository.findActiveOverride(_, _, _) >> { String rt, int gc, LocalDate date ->
            date == LocalDate.of(2026, 8, 2) ? Optional.of(override) : Optional.empty()
        }

        when:
        // Aug 1 -> Aug 3: two nights, the 2nd (Aug 2) covered by the override
        def result = reservationService.estimateTotal(Rate.RateType.NIGHTLY, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), null)

        then:
        result.subtotal().compareTo(BigDecimal.valueOf(280)) == 0
    }

    def "estimateTotal ignores rate overrides for a legacy-priced guest"() {
        given:
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        guestRepository.findById(7) >> Optional.of(legacyPricedGuest())
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }

        when:
        def result = reservationService.estimateTotal(Rate.RateType.NIGHTLY, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 7)

        then:
        0 * rateOverrideRepository.findActiveOverride(_, _, _)
        result.subtotal().compareTo(BigDecimal.valueOf(100)) == 0
    }

    def "estimateTotalWithExtras adds the priced extras to the room subtotal"() {
        given:
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        rateOverrideRepository.findActiveOverride(_, _, _) >> Optional.empty()
        guestRepository.findById(7) >> Optional.empty()
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        def selections = [new FolioService.ExtraSelection(2, 1)]
        folioService.priceExtras(selections, 2) >> BigDecimal.valueOf(50)

        when:
        def result = reservationService.estimateTotalWithExtras(Rate.RateType.NIGHTLY, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), null, selections)

        then:
        result.subtotal().compareTo(BigDecimal.valueOf(210)) == 0
        result.total().compareTo(BigDecimal.valueOf(210)) == 0
    }
}
