package com.staydesk.service

import com.staydesk.exception.InvalidReservationException
import com.staydesk.exception.RoomNotFoundException
import com.staydesk.exception.RoomUnavailableException
import com.staydesk.model.EncryptedString
import com.staydesk.model.Folio
import com.staydesk.model.Guest
import com.staydesk.model.Rate
import com.staydesk.model.Reservation
import com.staydesk.model.Room
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
    PaymentService paymentService = Mock()
    FolioService folioService = Mock()
    GuestRepository guestRepository = Mock()
    SmsService smsService = Mock()
    LockPasscodeService lockPasscodeService = Mock()
    ProviderFactory providerFactory = Mock()
    PosDeviceRepository posDeviceRepository = Mock()
    PaymentCredentialService paymentCredentialService = Mock()
    PiiCipher piiCipher = Mock()

    @Subject
    ReservationService reservationService = new ReservationService(reservationRepository, roomRepository, roomTypeRepository,
            folioRepository, rateRepository, paymentService, folioService, guestRepository, smsService,
            lockPasscodeService, providerFactory, posDeviceRepository, paymentCredentialService, piiCipher)

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
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
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
        folioRepository.getFolioByReservationId(1) >> Optional.of(folio)
        folioService.postCharge(_, _, _) >> folio

        when:
        reservationService.checkOut(1)

        then:
        1 * paymentCredentialService.scheduleExpiry(9, { LocalDateTime expiry ->
            expiry.isAfter(LocalDateTime.now().plusDays(29)) && expiry.isBefore(LocalDateTime.now().plusDays(31))
        })
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
                false, false, null, null, null, false, LocalDateTime.now(), LocalDateTime.now())
        def savedReservation = new Reservation(11, 9, 5, 2, LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 28),
                Reservation.ReservationStatus.CHECKED_IN, LocalDate.of(2026, 8, 21).atTime(15, 0), null,
                Rate.RateType.NIGHTLY, 1, Reservation.Channel.WALK_IN, false, LocalDateTime.now(), LocalDateTime.now(), "123456")

        roomRepository.findById(5) >> Optional.of(room)
        piiCipher.hash(_) >> "hashed-placeholder-email"
        guestRepository.findByEmailHash("hashed-placeholder-email") >> Optional.empty()
        reservationRepository.existsByConfirmationCode(_) >> false

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
        1 * roomRepository.updateRoomStatus(5, Room.RoomStatus.OCCUPIED)
        0 * paymentService._
        result.status() == Reservation.ReservationStatus.CHECKED_IN
    }

    def "backlogCheckIn reuses an existing guest found by email hash instead of creating a new one"() {
        given:
        def room = new Room(5, 26, 2, Room.RoomStatus.AVAILABLE, null, null, LocalDateTime.now(), LocalDateTime.now())
        def existingGuest = new Guest(3, new EncryptedString("James"), new EncryptedString("Reece"),
                new EncryptedString("james@example.com"), "hashed-real-email", new EncryptedString("5551234567"),
                true, false, null, null, null, false, LocalDateTime.now(), LocalDateTime.now())

        roomRepository.findById(5) >> Optional.of(room)
        piiCipher.hash("james@example.com") >> "hashed-real-email"
        guestRepository.findByEmailHash("hashed-real-email") >> Optional.of(existingGuest)
        reservationRepository.existsByConfirmationCode(_) >> false
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

    def "backlogCheckIn throws RoomUnavailableException when the room is already OCCUPIED"() {
        given:
        def room = new Room(5, 26, 2, Room.RoomStatus.OCCUPIED, null, null, LocalDateTime.now(), LocalDateTime.now())
        roomRepository.findById(5) >> Optional.of(room)

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
}
