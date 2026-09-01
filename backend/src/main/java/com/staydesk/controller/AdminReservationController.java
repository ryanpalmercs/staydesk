package com.staydesk.controller;

import com.staydesk.model.Reservation;
import com.staydesk.model.request.BacklogCheckInRequest;
import com.staydesk.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only entry points that bypass the normal reservation lifecycle's availability and
 * payment checks. Locked down to ROLE_ADMIN via the "/admin/**" rule in SecurityConfig.
 */
@RestController
@RequestMapping("/admin/reservations")
public class AdminReservationController {

    private final ReservationService reservationService;

    public AdminReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Records a guest as already checked in without going through the normal
     * reservation/check-in flow: no availability hold, no folio charge, no payment provider
     * call. For bringing Staydesk back in line with reality after staff have been operating
     * off paper (e.g. a system outage).
     */
    @PostMapping("/backlog-check-in")
    public ResponseEntity<Reservation> backlogCheckIn(@Valid @RequestBody BacklogCheckInRequest request) {
        return ResponseEntity.ok(reservationService.backlogCheckIn(request));
    }
}
