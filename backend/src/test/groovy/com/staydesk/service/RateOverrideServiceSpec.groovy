package com.staydesk.service

import com.staydesk.exception.InvalidRateOverrideException
import com.staydesk.exception.RateOverrideNotFoundException
import com.staydesk.exception.RateOverrideOverlapException
import com.staydesk.model.Rate
import com.staydesk.model.RateOverride
import com.staydesk.model.request.CreateRateOverrideRequest
import com.staydesk.model.Reservation
import com.staydesk.repository.RateOverrideRepository
import com.staydesk.repository.ReservationRepository
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class RateOverrideServiceSpec extends Specification {

    RateOverrideRepository rateOverrideRepository = Mock()
    ReservationRepository reservationRepository = Mock()
    ReservationService reservationService = Mock()

    RateOverrideService rateOverrideService = new RateOverrideService(rateOverrideRepository, reservationRepository, reservationService)

    def setup() {
        reservationRepository.findActiveByRateTypeAndGuestCountOverlapping(_, _, _, _) >> []
    }

    private static Reservation reservation(int id) {
        new Reservation(id, 9, 7, 3, 2, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 25),
                Reservation.ReservationStatus.CHECKED_IN, LocalDateTime.now(), null, Rate.RateType.NIGHTLY, 2,
                Reservation.Channel.WALK_IN, false, LocalDateTime.now(), LocalDateTime.now(), "123456")
    }

    private static CreateRateOverrideRequest request(LocalDate start = LocalDate.of(2026, 12, 20),
                                                      LocalDate end = LocalDate.of(2026, 12, 31),
                                                      Rate.RateType rateType = Rate.RateType.NIGHTLY) {
        new CreateRateOverrideRequest(rateType, 2, start, end, BigDecimal.valueOf(150), "Holiday surge")
    }

    def "creates a rate override when the date range doesn't overlap an existing one"() {
        given:
        rateOverrideRepository.findOverlapping("NIGHTLY", 2, request().startDate(), request().endDate(), null) >> []
        rateOverrideRepository.save(_) >> { args -> args[0] }

        when:
        def result = rateOverrideService.createRateOverride(request())

        then:
        result.rateType() == "NIGHTLY"
        result.guestCount() == 2
        result.amount() == BigDecimal.valueOf(150)
        result.label() == "Holiday surge"
    }

    def "throws InvalidRateOverrideException when end date is before start date"() {
        given:
        def badRequest = request(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 20))

        when:
        rateOverrideService.createRateOverride(badRequest)

        then:
        thrown(InvalidRateOverrideException)
        0 * rateOverrideRepository.save(_)
    }

    def "throws InvalidRateOverrideException when end date equals start date (zero nights covered)"() {
        given:
        def badRequest = request(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 20))

        when:
        rateOverrideService.createRateOverride(badRequest)

        then:
        thrown(InvalidRateOverrideException)
        0 * rateOverrideRepository.save(_)
    }

    def "creates a rate override for a WEEKLY_5 or WEEKLY_7 rate type"() {
        given:
        def weeklyRequest = request(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 31), rateType)
        rateOverrideRepository.findOverlapping(rateType.name(), 2, weeklyRequest.startDate(), weeklyRequest.endDate(), null) >> []
        rateOverrideRepository.save(_) >> { args -> args[0] }

        when:
        def result = rateOverrideService.createRateOverride(weeklyRequest)

        then:
        result.rateType() == rateType.name()

        where:
        rateType << [Rate.RateType.WEEKLY_5, Rate.RateType.WEEKLY_7]
    }

    def "an overlap check for a WEEKLY_5 override only looks at existing WEEKLY_5 overrides"() {
        given:
        def weeklyRequest = request(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 31), Rate.RateType.WEEKLY_5)

        when:
        rateOverrideService.createRateOverride(weeklyRequest)

        then:
        1 * rateOverrideRepository.findOverlapping("WEEKLY_5", 2, weeklyRequest.startDate(), weeklyRequest.endDate(), null) >> []
        1 * rateOverrideRepository.save(_) >> { args -> args[0] }
    }

    def "throws RateOverrideOverlapException when an existing override covers part of the same range"() {
        given:
        def existing = new RateOverride(9, "NIGHTLY", 2, LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 2),
                BigDecimal.valueOf(160), "New Year's", LocalDateTime.now(), LocalDateTime.now())
        rateOverrideRepository.findOverlapping("NIGHTLY", 2, request().startDate(), request().endDate(), null) >> [existing]

        when:
        rateOverrideService.createRateOverride(request())

        then:
        thrown(RateOverrideOverlapException)
        0 * rateOverrideRepository.save(_)
    }

    def "updateRateOverride excludes itself from the overlap check"() {
        given:
        def existing = new RateOverride(5, "NIGHTLY", 2, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10),
                BigDecimal.valueOf(140), "Old label", LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(1))
        rateOverrideRepository.findById(5) >> Optional.of(existing)
        rateOverrideRepository.findOverlapping("NIGHTLY", 2, request().startDate(), request().endDate(), 5) >> []
        rateOverrideRepository.save(_) >> { args -> args[0] }

        when:
        def result = rateOverrideService.updateRateOverride(5, request())

        then:
        result.id() == 5
        result.createdAt() == existing.createdAt()
        result.label() == "Holiday surge"
    }

    def "updateRateOverride throws RateOverrideNotFoundException when the override doesn't exist"() {
        given:
        rateOverrideRepository.findById(5) >> Optional.empty()

        when:
        rateOverrideService.updateRateOverride(5, request())

        then:
        thrown(RateOverrideNotFoundException)
        0 * rateOverrideRepository.save(_)
    }

    def "deleteRateOverride throws RateOverrideNotFoundException when the override doesn't exist"() {
        given:
        rateOverrideRepository.findById(5) >> Optional.empty()

        when:
        rateOverrideService.deleteRateOverride(5)

        then:
        thrown(RateOverrideNotFoundException)
        0 * rateOverrideRepository.deleteById(_)
    }

    def "deleteRateOverride removes the override when it exists"() {
        given:
        def existing = new RateOverride(5, "NIGHTLY", 2, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10),
                BigDecimal.valueOf(140), "Old label", LocalDateTime.now(), LocalDateTime.now())
        rateOverrideRepository.findById(5) >> Optional.of(existing)

        when:
        rateOverrideService.deleteRateOverride(5)

        then:
        1 * rateOverrideRepository.deleteById(5)
    }

    def "createRateOverride reconciles already-posted charges on reservations the new override covers"() {
        given:
        def affected = reservation(1)
        rateOverrideRepository.findOverlapping(_, _, _, _, _) >> []
        rateOverrideRepository.save(_) >> { args -> args[0] }

        when:
        rateOverrideService.createRateOverride(request())

        then:
        1 * reservationRepository.findActiveByRateTypeAndGuestCountOverlapping("NIGHTLY", 2, request().startDate(), request().endDate()) >> [affected]
        1 * reservationService.reconcileRoomCharges(affected)
    }

    def "updateRateOverride reconciles reservations under both the old and the new range"() {
        given:
        def existing = new RateOverride(5, "NIGHTLY", 2, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                BigDecimal.valueOf(140), "Old label", LocalDateTime.now(), LocalDateTime.now())
        def oldRangeReservation = reservation(1)
        def newRangeReservation = reservation(2)
        rateOverrideRepository.findById(5) >> Optional.of(existing)
        rateOverrideRepository.findOverlapping(_, _, _, _, _) >> []
        rateOverrideRepository.save(_) >> { args -> args[0] }

        when:
        rateOverrideService.updateRateOverride(5, request())

        then:
        1 * reservationRepository.findActiveByRateTypeAndGuestCountOverlapping("NIGHTLY", 2,
                existing.startDate(), existing.endDate()) >> [oldRangeReservation]
        1 * reservationRepository.findActiveByRateTypeAndGuestCountOverlapping("NIGHTLY", 2,
                request().startDate(), request().endDate()) >> [newRangeReservation]
        1 * reservationService.reconcileRoomCharges(oldRangeReservation)
        1 * reservationService.reconcileRoomCharges(newRangeReservation)
    }

    def "deleteRateOverride reconciles affected reservations after the override is gone"() {
        given:
        def existing = new RateOverride(5, "NIGHTLY", 2, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10),
                BigDecimal.valueOf(140), "Old label", LocalDateTime.now(), LocalDateTime.now())
        def affected = reservation(1)
        rateOverrideRepository.findById(5) >> Optional.of(existing)

        when:
        rateOverrideService.deleteRateOverride(5)

        then:
        1 * rateOverrideRepository.deleteById(5)

        then:
        1 * reservationRepository.findActiveByRateTypeAndGuestCountOverlapping("NIGHTLY", 2,
                existing.startDate(), existing.endDate()) >> [affected]
        1 * reservationService.reconcileRoomCharges(affected)
    }

    def "reconcileAllActiveOverrides reconciles affected reservations for every existing override and returns the count checked"() {
        given:
        def override1 = new RateOverride(1, "NIGHTLY", 2, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10),
                BigDecimal.valueOf(140), "Holiday", LocalDateTime.now(), LocalDateTime.now())
        def override2 = new RateOverride(2, "WEEKLY_5", 4, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 10),
                BigDecimal.valueOf(600), "New Year", LocalDateTime.now(), LocalDateTime.now())
        def reservationA = reservation(1)
        def reservationB = reservation(2)
        rateOverrideRepository.findAll() >> [override1, override2]

        when:
        def checked = rateOverrideService.reconcileAllActiveOverrides()

        then:
        1 * reservationRepository.findActiveByRateTypeAndGuestCountOverlapping("NIGHTLY", 2,
                override1.startDate(), override1.endDate()) >> [reservationA]
        1 * reservationRepository.findActiveByRateTypeAndGuestCountOverlapping("WEEKLY_5", 4,
                override2.startDate(), override2.endDate()) >> [reservationB]
        1 * reservationService.reconcileRoomCharges(reservationA)
        1 * reservationService.reconcileRoomCharges(reservationB)
        checked == 2
    }
}
