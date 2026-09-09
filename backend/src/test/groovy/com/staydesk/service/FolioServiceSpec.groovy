package com.staydesk.service

import com.staydesk.exception.ExtraNotFoundException
import com.staydesk.exception.FolioClosedException
import com.staydesk.exception.FolioNotFoundException
import com.staydesk.model.Extra
import com.staydesk.model.Folio
import com.staydesk.model.PropertySetting
import com.staydesk.model.Reservation
import com.staydesk.repository.ExtraRepository
import com.staydesk.repository.FolioItemRepository
import com.staydesk.repository.FolioRepository
import com.staydesk.repository.ReservationRepository
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class FolioServiceSpec extends Specification {

    FolioRepository folioRepository = Mock()
    FolioItemRepository folioItemRepository = Mock()
    ExtraRepository extraRepository = Mock()
    ReservationRepository reservationRepository = Mock()
    PropertySettingsService propertySettingsService = Mock()

    FolioService service = new FolioService(folioRepository, folioItemRepository, extraRepository,
            reservationRepository, propertySettingsService)

    private static Folio openFolio() {
        new Folio(1, 10, Folio.FolioStatus.OPEN, BigDecimal.ZERO, null, LocalDateTime.now(), LocalDateTime.now())
    }

    private static Reservation reservationFor(LocalDate checkIn, LocalDate checkOut) {
        new Reservation(10, 1, 1, 1, checkIn, checkOut, Reservation.ReservationStatus.CHECKED_IN, null, null,
                com.staydesk.model.Rate.RateType.NIGHTLY, 1, Reservation.Channel.WALK_IN, false,
                LocalDateTime.now(), LocalDateTime.now(), null)
    }

    private static Extra flatExtra() {
        new Extra(1, "Late Checkout", BigDecimal.valueOf(20), true, Extra.BillingType.FLAT,
                LocalDateTime.now(), LocalDateTime.now())
    }

    private static Extra perNightExtra() {
        new Extra(2, "Pet Fee", BigDecimal.valueOf(25), true, Extra.BillingType.PER_NIGHT,
                LocalDateTime.now(), LocalDateTime.now())
    }

    def setup() {
        propertySettingsService.getProperty("lodging_tax_rate") >> new PropertySetting("lodging_tax_rate", "0", LocalDateTime.now(), LocalDateTime.now())
    }

    def "addExtra charges a FLAT extra as price times quantity, without touching the reservation"() {
        given:
        folioRepository.findById(1) >> Optional.of(openFolio())
        extraRepository.findById(1) >> Optional.of(flatExtra())
        folioItemRepository.save(_) >> { it[0] }
        folioRepository.save(_) >> { it[0] }

        when:
        def result = service.addExtra(1, 1, 2)

        then:
        0 * reservationRepository.findById(_)
        1 * folioItemRepository.save({ it.description() == "Late Checkout x2" && it.amount().compareTo(BigDecimal.valueOf(40)) == 0 })
        result.total().compareTo(BigDecimal.valueOf(40)) == 0
    }

    def "addExtra charges a PER_NIGHT extra as price times quantity times nights of stay"() {
        given:
        folioRepository.findById(1) >> Optional.of(openFolio())
        extraRepository.findById(2) >> Optional.of(perNightExtra())
        reservationRepository.findById(10) >> Optional.of(reservationFor(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)))
        folioItemRepository.save(_) >> { it[0] }
        folioRepository.save(_) >> { it[0] }

        when:
        def result = service.addExtra(1, 2, 1)

        then:
        1 * folioItemRepository.save({ it.description() == "Pet Fee x3 nights" && it.amount().compareTo(BigDecimal.valueOf(75)) == 0 })
        result.total().compareTo(BigDecimal.valueOf(75)) == 0
    }

    def "addExtra multiplies quantity and nights together for a PER_NIGHT extra"() {
        given:
        folioRepository.findById(1) >> Optional.of(openFolio())
        extraRepository.findById(2) >> Optional.of(perNightExtra())
        reservationRepository.findById(10) >> Optional.of(reservationFor(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)))
        folioItemRepository.save(_) >> { it[0] }
        folioRepository.save(_) >> { it[0] }

        when:
        def result = service.addExtra(1, 2, 2)

        then:
        1 * folioItemRepository.save({ it.description() == "Pet Fee x2 x3 nights" && it.amount().compareTo(BigDecimal.valueOf(150)) == 0 })
        result.total().compareTo(BigDecimal.valueOf(150)) == 0
    }

    def "addExtra throws FolioClosedException for a CLOSED folio"() {
        given:
        def closedFolio = new Folio(1, 10, Folio.FolioStatus.CLOSED, BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now())
        folioRepository.findById(1) >> Optional.of(closedFolio)

        when:
        service.addExtra(1, 1, 1)

        then:
        thrown(FolioClosedException)
        0 * extraRepository.findById(_)
    }

    def "addExtra throws FolioNotFoundException when the folio does not exist"() {
        given:
        folioRepository.findById(1) >> Optional.empty()

        when:
        service.addExtra(1, 1, 1)

        then:
        thrown(FolioNotFoundException)
    }

    def "addExtra throws ExtraNotFoundException for an inactive extra"() {
        given:
        folioRepository.findById(1) >> Optional.of(openFolio())
        def inactive = new Extra(1, "Retired Extra", BigDecimal.TEN, false, Extra.BillingType.FLAT,
                LocalDateTime.now(), LocalDateTime.now())
        extraRepository.findById(1) >> Optional.of(inactive)

        when:
        service.addExtra(1, 1, 1)

        then:
        thrown(ExtraNotFoundException)
    }
}
