package com.staydesk.service

import com.staydesk.exception.InvalidRateOverrideException
import com.staydesk.exception.RateOverrideNotFoundException
import com.staydesk.exception.RateOverrideOverlapException
import com.staydesk.model.Rate
import com.staydesk.model.RateOverride
import com.staydesk.model.request.CreateRateOverrideRequest
import com.staydesk.repository.RateOverrideRepository
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class RateOverrideServiceSpec extends Specification {

    RateOverrideRepository rateOverrideRepository = Mock()

    RateOverrideService rateOverrideService = new RateOverrideService(rateOverrideRepository)

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

    def "throws InvalidRateOverrideException for a non-NIGHTLY rate type"() {
        given:
        def weeklyRequest = request(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 31), Rate.RateType.WEEKLY_5)

        when:
        rateOverrideService.createRateOverride(weeklyRequest)

        then:
        thrown(InvalidRateOverrideException)
        0 * rateOverrideRepository.save(_)
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
}
