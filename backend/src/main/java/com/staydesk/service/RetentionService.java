package com.staydesk.service;

import com.staydesk.model.Guest;
import com.staydesk.model.Reservation;
import com.staydesk.repository.GuestRepository;
import com.staydesk.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class RetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionService.class);
    private static final Period RETENTION_PERIOD = Period.ofYears(3);

    private final GuestRepository guestRepository;
    private final ReservationRepository reservationRepository;

    public RetentionService(GuestRepository guestRepository, ReservationRepository reservationRepository) {
        this.guestRepository = guestRepository;
        this.reservationRepository = reservationRepository;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "America/Chicago")
    public void runAnonymizationJob() {
        int anonymized = anonymizeEligibleGuests();
        LOGGER.info("Anonymization job completed. {} guest(s) anonymized.", anonymized);
    }

    @Transactional
    public int anonymizeEligibleGuests() {
        LocalDate cutoff = LocalDate.now().minus(RETENTION_PERIOD);
        int anonymized = 0;

        for (Guest guest : guestRepository.findAll()) {
            if (guest.flagged() || guest.legalHold()) {
                continue;
            }

            if (isEligibleForAnonymization(guest, cutoff)) {
                reservationRepository.anonymizeByGuestId(guest.id());
                guestRepository.deleteById(guest.id());
                anonymized++;
            }
        }

        return anonymized;
    }

    private boolean isEligibleForAnonymization(Guest guest, LocalDate cutoff) {
        List<Reservation> reservations = reservationRepository.findByGuestId(guest.id());

        if (reservations.isEmpty()) {
            return false;
        }

        for (Reservation reservation : reservations) {
            if (reservation.legalHold()) {
                return false;
            }

            if (reservation.status() == Reservation.ReservationStatus.CONFIRMED
                || reservation.status() == Reservation.ReservationStatus.CHECKED_IN) {
                return false;
            }
        }

        LocalDate mostRecentRelevantDate = reservations.stream()
                                                       .map(this::relevantDate)
                                                       .max(LocalDate::compareTo)
                                                       .orElseThrow();

        return mostRecentRelevantDate.isBefore(cutoff);
    }

    private LocalDate relevantDate(Reservation reservation) {
        return switch (reservation.status()) {
            case CHECKED_OUT -> reservation.checkOutDate();
            case CANCELLED -> reservation.updatedAt().toLocalDate();
            default -> throw new IllegalStateException("Unexpected status: " + reservation.status());
        };
    }
}