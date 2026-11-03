package com.staydesk.service

import com.staydesk.model.EncryptedString
import com.staydesk.model.Guest
import com.staydesk.model.Rate
import com.staydesk.model.Reservation
import com.staydesk.repository.GuestRepository
import com.staydesk.repository.ReservationRepository
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class GuestServiceSpec extends Specification {

    GuestRepository guestRepository = Mock()
    ReservationRepository reservationRepository = Mock()
    ReservationService reservationService = Mock()

    GuestService service = new GuestService(guestRepository, reservationRepository, reservationService)

    private static Guest guest() {
        new Guest(7, new EncryptedString("James"), new EncryptedString("Reece"), new EncryptedString("james@example.com"),
                "hash", new EncryptedString("5551234567"), false, false, null, null, null, false,
                false, null, Rate.RateType.NIGHTLY, true, Guest.GuestType.INDIVIDUAL, "", LocalDateTime.now(), LocalDateTime.now())
    }

    private static Reservation reservationFor(int id) {
        new Reservation(id, 9, 7, 3, 2, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 13),
                Reservation.ReservationStatus.CHECKED_IN, null, null, Rate.RateType.NIGHTLY, 1, Reservation.Channel.WALK_IN, false,
                LocalDateTime.now(), LocalDateTime.now(), "123456")
    }

    def "updateGuest saves the guest and reconciles each of their reservations"() {
        given:
        def updatedGuest = guest()
        def res1 = reservationFor(1)
        def res2 = reservationFor(2)

        guestRepository.save(updatedGuest) >> updatedGuest
        reservationRepository.findByGuestId(7) >> [res1, res2]

        when:
        def result = service.updateGuest(updatedGuest)

        then:
        1 * reservationService.reconcileRoomCharges(res1)
        1 * reservationService.reconcileRoomCharges(res2)
        result == updatedGuest
    }

    def "updateGuest reconciles nothing when the guest has no reservations"() {
        given:
        def updatedGuest = guest()

        guestRepository.save(updatedGuest) >> updatedGuest
        reservationRepository.findByGuestId(7) >> []

        when:
        service.updateGuest(updatedGuest)

        then:
        0 * reservationService.reconcileRoomCharges(_)
    }
}
