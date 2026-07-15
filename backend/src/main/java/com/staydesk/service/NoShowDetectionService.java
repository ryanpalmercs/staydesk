package com.staydesk.service;

import com.staydesk.model.Reservation;
import com.staydesk.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class NoShowDetectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoShowDetectionService.class);
    private static final ZoneId PROPERTY_ZONE = ZoneId.of("America/Chicago");

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    public NoShowDetectionService(ReservationRepository reservationRepository, ReservationService reservationService) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "America/Chicago")
    public void detectNoShows() {
        List<Reservation> candidates = reservationRepository.findNoShowCandidates(LocalDate.now(PROPERTY_ZONE));
        int marked = 0;

        for (Reservation reservation : candidates) {
            try {
                reservationService.markNoShow(reservation.id());
                marked++;
            } catch (Exception e) {
                LOGGER.error("Failed to mark reservation {} as no-show", reservation.id(), e);
            }
        }

        LOGGER.info("No-show detection job completed. {}/{} reservation(s) marked NO_SHOW.", marked, candidates.size());
    }
}