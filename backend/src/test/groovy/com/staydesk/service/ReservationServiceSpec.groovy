package com.staydesk.service

import com.staydesk.exception.InvalidReservationException
import com.staydesk.exception.RoomTypeUnavailableException
import com.staydesk.exception.StayAlreadySettledException
import com.staydesk.exception.StayNotSettledException
import com.staydesk.model.Folio
import com.staydesk.model.Rate
import com.staydesk.model.Reservation
import com.staydesk.model.Room
import com.staydesk.model.RoomType
import com.staydesk.model.request.CreateMultiRoomReservationRequest
import com.staydesk.provider.ProviderFactory
import com.staydesk.repository.*
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

    @Subject
    ReservationService reservationService = new ReservationService(reservationRepository, roomRepository, roomTypeRepository,
            folioRepository, rateRepository, paymentService, folioService, guestRepository, smsService,
            lockPasscodeService, providerFactory, posDeviceRepository, paymentCredentialService)

    private static Reservation reservation(Reservation.ReservationStatus status, Reservation.Channel channel,
                                           Rate.RateType rateType = Rate.RateType.NIGHTLY) {
        new Reservation(1, 9, 7, 3, 2, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 13),
                status, null, null, rateType, 1, channel, false, LocalDateTime.now(), LocalDateTime.now(), "123456")
    }

    def "marks a CONFIRMED PHONE reservation as NO_SHOW, refunds all but first night, and closes the folio"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE)
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        folioRepository.findById(9) >> Optional.of(folio)
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.markNoShow(1)

        then:
        1 * paymentService.refundReservationShare(folio,
                { BigDecimal share -> share.compareTo(BigDecimal.valueOf(240)) == 0 },
                { BigDecimal retain -> retain.compareTo(BigDecimal.valueOf(80)) == 0 })
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
        0 * paymentService.refundReservationShare(_, _, _)
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

    def "transitions status without throwing when the reservation's folio can't be found"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE)
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        folioRepository.findById(9) >> Optional.empty()
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.markNoShow(1)

        then:
        0 * paymentService.refundReservationShare(_, _, _)
        0 * folioRepository.closeFolio(_)
        result.status() == Reservation.ReservationStatus.NO_SHOW
    }

    @Unroll
    def "computes the pre-tax first-night base amount correctly for #rateType"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE, rateType)
        def rate = new Rate(1, rateType.name(), 1, rateAmount, LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.valueOf(500), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(rateType, 1) >> Optional.of(rate)
        folioRepository.findById(9) >> Optional.of(folio)
        reservationRepository.save(_) >> { Reservation r -> r }
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }   // identity: isolates division math from tax logic

        when:
        reservationService.markNoShow(1)

        then:
        1 * paymentService.refundReservationShare(folio, _, { BigDecimal amt -> amt.compareTo(expectedBase) == 0 })

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
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        folioRepository.findById(9) >> Optional.of(folio)
        folioService.postCharge(_, _, _) >> folio

        when:
        reservationService.checkOut(1)

        then:
        1 * paymentCredentialService.scheduleExpiry(9, { LocalDateTime expiry ->
            expiry.isAfter(LocalDateTime.now().plusDays(29)) && expiry.isBefore(LocalDateTime.now().plusDays(31))
        })
    }

    def "checkOut does not close the folio or schedule credential expiry when a sibling reservation is still active"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CHECKED_IN, Reservation.Channel.PHONE)
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        folioRepository.findById(9) >> Optional.of(folio)
        folioService.postCharge(_, _, _) >> folio
        reservationRepository.existsOtherActiveByFolioId(9, 1) >> true

        when:
        reservationService.checkOut(1)

        then:
        0 * folioRepository.save(_)
        0 * paymentCredentialService.scheduleExpiry(_, _)
    }

    def "cancelReservation voids/refunds everything and closes the folio when it's the last active reservation"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE)
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.valueOf(240), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.existsOtherActiveByFolioId(9, 1) >> false
        folioRepository.findById(9) >> Optional.of(folio)
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.cancelReservation(1)

        then:
        1 * paymentService.cancelOpenHolds(folio)
        1 * folioRepository.closeFolio(9)
        0 * paymentService.refundReservationShare(*_)
        result.status() == Reservation.ReservationStatus.CANCELLED
    }

    def "cancelReservation refunds only this reservation's share and leaves the folio open when a sibling is still active"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE)
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.valueOf(480), null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        reservationRepository.existsOtherActiveByFolioId(9, 1) >> true
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        folioRepository.findById(9) >> Optional.of(folio)
        reservationRepository.save(_) >> { Reservation r -> r }

        when:
        def result = reservationService.cancelReservation(1)

        then:
        0 * paymentService.cancelOpenHolds(_)
        0 * folioRepository.closeFolio(_)
        1 * paymentService.refundReservationShare(folio, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(240)) == 0 }, BigDecimal.ZERO)
        result.status() == Reservation.ReservationStatus.CANCELLED
    }

    def "checkIn throws StayNotSettledException for an unsettled WALK_IN reservation"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN)
        reservationRepository.findById(1) >> Optional.of(res)
        paymentService.isRoomPaymentSettled(9) >> false

        when:
        reservationService.checkIn(1, 5, "incidentals-token")

        then:
        thrown(StayNotSettledException)
        0 * roomRepository.findAvailableOfType(*_)
    }

    def "checkIn proceeds once the stay is already settled for a WALK_IN reservation"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN)
        def room = new Room(5, 101, 2, Room.RoomStatus.AVAILABLE, null, null, LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        paymentService.isRoomPaymentSettled(9) >> true
        roomRepository.findAvailableOfType(2, res.checkOutDate(), res.checkInDate()) >> [room]
        folioRepository.findById(9) >> Optional.of(folio)
        lockPasscodeService.issuePasscode(_, _) >> new LockPasscodeService.PasscodeResult(LockPasscodeService.PasscodeResult.Outcome.ISSUED, null)
        guestRepository.findById(7) >> Optional.empty()

        when:
        reservationService.checkIn(1, 5, "incidentals-token")

        then:
        noExceptionThrown()
        1 * paymentService.createIncidentalHold(folio, 1, _, "incidentals-token")
    }

    def "checkIn never checks settlement for a PHONE reservation"() {
        given:
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.PHONE)
        def room = new Room(5, 101, 2, Room.RoomStatus.AVAILABLE, null, null, LocalDateTime.now(), LocalDateTime.now())
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())

        reservationRepository.findById(1) >> Optional.of(res)
        roomRepository.findAvailableOfType(2, res.checkOutDate(), res.checkInDate()) >> [room]
        folioRepository.findById(9) >> Optional.of(folio)
        lockPasscodeService.issuePasscode(_, _) >> new LockPasscodeService.PasscodeResult(LockPasscodeService.PasscodeResult.Outcome.NO_LOCK_ASSIGNED, null)

        when:
        reservationService.checkIn(1, 5, "incidentals-token")

        then:
        0 * paymentService.isRoomPaymentSettled(_)
    }

    def "settleWalkInStay charges the combined total across every reservation sharing the folio"() {
        given:
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def res1 = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN)
        def res2 = new Reservation(2, 9, 7, null, 4, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 13),
                Reservation.ReservationStatus.CONFIRMED, null, null, Rate.RateType.NIGHTLY, 1, Reservation.Channel.WALK_IN,
                false, LocalDateTime.now(), LocalDateTime.now(), "654321")
        def rate = new Rate(1, "NIGHTLY", 1, BigDecimal.valueOf(80), LocalDateTime.now(), LocalDateTime.now())

        folioRepository.findById(9) >> Optional.of(folio)
        reservationRepository.findByFolioId(9) >> [res1, res2]
        paymentService.isRoomPaymentSettled(9) >> false
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 1) >> Optional.of(rate)
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        providerFactory.getPaymentProviderName() >> "authorizenet"

        when:
        reservationService.settleWalkInStay(9, "room-token")

        then:
        1 * paymentService.chargeFullStay(folio, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(480)) == 0 }, "authorizenet", "room-token")
    }

    def "settleWalkInStay throws StayAlreadySettledException when the folio's room payment is already captured"() {
        given:
        def folio = new Folio(9, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def res = reservation(Reservation.ReservationStatus.CONFIRMED, Reservation.Channel.WALK_IN)

        folioRepository.findById(9) >> Optional.of(folio)
        reservationRepository.findByFolioId(9) >> [res]
        paymentService.isRoomPaymentSettled(9) >> true

        when:
        reservationService.settleWalkInStay(9, "room-token")

        then:
        thrown(StayAlreadySettledException)
        0 * paymentService.chargeFullStay(*_)
    }

    def "createMultiRoomReservation creates one reservation per room line sharing a folio, charges the combined total once for PHONE"() {
        given:
        def savedFolio = new Folio(20, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def roomType1 = new RoomType(1, "QUEEN", 5, 0, LocalDateTime.now(), LocalDateTime.now())
        def roomType2 = new RoomType(2, "SUITE", 2, 0, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 2, BigDecimal.valueOf(100), LocalDateTime.now(), LocalDateTime.now())

        folioRepository.save(_) >> savedFolio
        roomTypeRepository.findById(1) >> Optional.of(roomType1)
        roomTypeRepository.findById(2) >> Optional.of(roomType2)
        reservationRepository.countOverlappingByRoomType(*_) >> 0
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 2) >> Optional.of(rate)
        reservationRepository.existsByConfirmationCode(_) >> false
        reservationRepository.save(_) >> { Reservation r -> r }
        folioService.postCharge(_, _, _) >> savedFolio
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        guestRepository.findById(5) >> Optional.empty()

        def rooms = [
                new CreateMultiRoomReservationRequest.RoomLine(1, 1),
                new CreateMultiRoomReservationRequest.RoomLine(2, 1)
        ]

        when:
        def result = reservationService.createMultiRoomReservation(5, rooms, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22),
                Rate.RateType.NIGHTLY, 2, Reservation.Channel.PHONE, "token-1")

        then:
        result.size() == 2
        result.every { it.folioId() == 20 }
        1 * paymentService.chargeFullStay(savedFolio, { BigDecimal amt -> amt.compareTo(BigDecimal.valueOf(400)) == 0 }, _, "token-1")
    }

    def "createMultiRoomReservation does not charge at creation for WALK_IN"() {
        given:
        def savedFolio = new Folio(21, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def roomType = new RoomType(1, "QUEEN", 5, 0, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 2, BigDecimal.valueOf(100), LocalDateTime.now(), LocalDateTime.now())

        folioRepository.save(_) >> savedFolio
        roomTypeRepository.findById(1) >> Optional.of(roomType)
        reservationRepository.countOverlappingByRoomType(*_) >> 0
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 2) >> Optional.of(rate)
        reservationRepository.existsByConfirmationCode(_) >> false
        reservationRepository.save(_) >> { Reservation r -> r }
        folioService.postCharge(_, _, _) >> savedFolio
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }
        guestRepository.findById(5) >> Optional.empty()

        def rooms = [new CreateMultiRoomReservationRequest.RoomLine(1, 2)]

        when:
        def result = reservationService.createMultiRoomReservation(5, rooms, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22),
                Rate.RateType.NIGHTLY, 2, Reservation.Channel.WALK_IN, null)

        then:
        result.size() == 2
        0 * paymentService.chargeFullStay(*_)
    }

    def "createMultiRoomReservation throws InvalidReservationException for an empty room list"() {
        when:
        reservationService.createMultiRoomReservation(5, [], LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22),
                Rate.RateType.NIGHTLY, 2, Reservation.Channel.WALK_IN, null)

        then:
        thrown(InvalidReservationException)
        0 * folioRepository.save(_)
    }

    def "createMultiRoomReservation propagates an availability failure on a later room without charging"() {
        given:
        def savedFolio = new Folio(22, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
        def roomType1 = new RoomType(1, "QUEEN", 5, 0, LocalDateTime.now(), LocalDateTime.now())
        def roomType2 = new RoomType(2, "SUITE", 1, 0, LocalDateTime.now(), LocalDateTime.now())
        def rate = new Rate(1, "NIGHTLY", 2, BigDecimal.valueOf(100), LocalDateTime.now(), LocalDateTime.now())

        folioRepository.save(_) >> savedFolio
        roomTypeRepository.findById(1) >> Optional.of(roomType1)
        roomTypeRepository.findById(2) >> Optional.of(roomType2)
        reservationRepository.countOverlappingByRoomType(1, _, _) >> 0
        reservationRepository.countOverlappingByRoomType(2, _, _) >> 1
        rateRepository.findByRateTypeAndGuestCount(Rate.RateType.NIGHTLY, 2) >> Optional.of(rate)
        reservationRepository.existsByConfirmationCode(_) >> false
        reservationRepository.save(_) >> { Reservation r -> r }
        folioService.postCharge(_, _, _) >> savedFolio
        folioService.estimateWithTax(_) >> { BigDecimal base -> base }

        def rooms = [
                new CreateMultiRoomReservationRequest.RoomLine(1, 1),
                new CreateMultiRoomReservationRequest.RoomLine(2, 1)
        ]

        when:
        reservationService.createMultiRoomReservation(5, rooms, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22),
                Rate.RateType.NIGHTLY, 2, Reservation.Channel.PHONE, "token-1")

        then:
        thrown(RoomTypeUnavailableException)
        0 * paymentService.chargeFullStay(*_)
    }
}
