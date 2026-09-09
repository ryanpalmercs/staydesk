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
        1 * folioItemRepository.save({ it.description() == "LATE CHECKOUT X2" && it.amount().compareTo(BigDecimal.valueOf(40)) == 0 &&
                it.extraId() == 1 && it.quantity() == 2 })
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
        3 * folioItemRepository.save({ it.type() == com.staydesk.model.FolioItem.FolioItemType.CHARGE && it.description() == "PET FEE" &&
                it.amount().compareTo(BigDecimal.valueOf(25)) == 0 && it.extraId() == 2 && it.quantity() == 1 })
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
        3 * folioItemRepository.save({ it.type() == com.staydesk.model.FolioItem.FolioItemType.CHARGE && it.description() == "PET FEE X2" &&
                it.amount().compareTo(BigDecimal.valueOf(50)) == 0 && it.extraId() == 2 && it.quantity() == 2 })
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

    def "distinctPerNightExtras returns only PER_NIGHT extras, deduped by extra with their original quantity"() {
        given:
        def flatItem = new com.staydesk.model.FolioItem(1, 1, "Late Checkout", BigDecimal.valueOf(20),
                com.staydesk.model.FolioItem.FolioItemType.CHARGE, 1, 1, LocalDateTime.now(), LocalDateTime.now())
        def petFeeFirst = new com.staydesk.model.FolioItem(2, 1, "Pet Fee x3 nights", BigDecimal.valueOf(75),
                com.staydesk.model.FolioItem.FolioItemType.CHARGE, 2, 2, LocalDateTime.now(), LocalDateTime.now())
        def petFeeTopUp = new com.staydesk.model.FolioItem(3, 1, "Pet Fee x2 x2 nights (extended)", BigDecimal.valueOf(100),
                com.staydesk.model.FolioItem.FolioItemType.CHARGE, 2, 2, LocalDateTime.now(), LocalDateTime.now())
        def taxItem = new com.staydesk.model.FolioItem(4, 1, "Pet Fee x3 nights TAX", BigDecimal.valueOf(5),
                com.staydesk.model.FolioItem.FolioItemType.TAX, null, null, LocalDateTime.now(), LocalDateTime.now())

        folioItemRepository.findByFolioId(1) >> [flatItem, petFeeFirst, petFeeTopUp, taxItem]
        extraRepository.findById(1) >> Optional.of(flatExtra())
        extraRepository.findById(2) >> Optional.of(perNightExtra())

        when:
        def result = service.distinctPerNightExtras(1)

        then:
        result.size() == 1
        result[0].extraId() == 2
        result[0].extraName() == "Pet Fee"
        result[0].unitPrice().compareTo(BigDecimal.valueOf(25)) == 0
        result[0].quantity() == 2
    }

    def "priceExtras sums FLAT extras once and PER_NIGHT extras by nights"() {
        given:
        extraRepository.findById(1) >> Optional.of(flatExtra())
        extraRepository.findById(2) >> Optional.of(perNightExtra())

        when:
        def result = service.priceExtras([
                new FolioService.ExtraSelection(1, 2),
                new FolioService.ExtraSelection(2, 1)
        ], 3)

        then:
        result.compareTo(BigDecimal.valueOf(115)) == 0
    }

    def "postCharge links the CHARGE item to an extra and quantity, but not the TAX item"() {
        given:
        propertySettingsService.getProperty("lodging_tax_rate") >> new PropertySetting("lodging_tax_rate", "0.1", LocalDateTime.now(), LocalDateTime.now())
        folioItemRepository.save(_) >> { it[0] }
        folioRepository.save(_) >> { it[0] }

        when:
        service.postCharge(openFolio(), "Pet Fee x3 nights (extended)", BigDecimal.valueOf(50), 2, 1)

        then:
        1 * folioItemRepository.save({ it.type() == com.staydesk.model.FolioItem.FolioItemType.CHARGE && it.extraId() == 2 && it.quantity() == 1 })
        1 * folioItemRepository.save({ it.type() == com.staydesk.model.FolioItem.FolioItemType.TAX && it.extraId() == null && it.quantity() == null })
    }
}
