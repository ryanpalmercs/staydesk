package com.staydesk.service

import com.staydesk.model.Rate
import com.staydesk.model.Reservation
import com.staydesk.repository.ReservationRepository
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class NoShowDetectionServiceSpec extends Specification {

    ReservationRepository reservationRepository = Mock()
    ReservationService reservationService = Mock()

    NoShowDetectionService noShowDetectionService = new NoShowDetectionService(reservationRepository, reservationService)

    private static Reservation candidate(int id) {
        new Reservation(id, 1, null, 1, LocalDate.now().minusDays(1), LocalDate.now().plusDays(2),
                Reservation.ReservationStatus.CONFIRMED, null, null, Rate.RateType.NIGHTLY,
                1, Reservation.Channel.PHONE, false, LocalDateTime.now(), LocalDateTime.now(), "123456")
    }

    def "marks every candidate reservation as a no-show"() {
        given:
        reservationRepository.findNoShowCandidates(_ as LocalDate) >> [candidate(1), candidate(2), candidate(3)]

        when:
        noShowDetectionService.detectNoShows()

        then:
        1 * reservationService.markNoShow(1)
        1 * reservationService.markNoShow(2)
        1 * reservationService.markNoShow(3)
    }

    def "isolates failures so one bad candidate doesn't block the rest"() {
        given:
        reservationRepository.findNoShowCandidates(_ as LocalDate) >> [candidate(1), candidate(2)]
        reservationService.markNoShow(1) >> { throw new RuntimeException("refund failed") }

        when:
        noShowDetectionService.detectNoShows()

        then:
        noExceptionThrown()
        1 * reservationService.markNoShow(1)
        1 * reservationService.markNoShow(2)
    }

    def "does nothing when there are no candidates"() {
        given:
        reservationRepository.findNoShowCandidates(_ as LocalDate) >> []

        when:
        noShowDetectionService.detectNoShows()

        then:
        0 * reservationService.markNoShow(_)
    }
}