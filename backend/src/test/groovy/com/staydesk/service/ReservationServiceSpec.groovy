package com.staydesk.service

import com.staydesk.exception.InvalidReservationException
import com.staydesk.model.Folio
import com.staydesk.model.Rate
import com.staydesk.model.Reservation
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
}
