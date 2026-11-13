package com.staydesk.service;

import com.staydesk.exception.InvalidRateOverrideException;
import com.staydesk.exception.RateOverrideNotFoundException;
import com.staydesk.exception.RateOverrideOverlapException;
import com.staydesk.model.RateOverride;
import com.staydesk.model.Reservation;
import com.staydesk.model.request.CreateRateOverrideRequest;
import com.staydesk.repository.RateOverrideRepository;
import com.staydesk.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RateOverrideService {

    private final RateOverrideRepository rateOverrideRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    public RateOverrideService(RateOverrideRepository rateOverrideRepository, ReservationRepository reservationRepository,
                               ReservationService reservationService) {
        this.rateOverrideRepository = rateOverrideRepository;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
    }

    public List<RateOverride> getRateOverrides() {
        return rateOverrideRepository.findAll();
    }

    private void validate(CreateRateOverrideRequest request, Integer excludeId) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new InvalidRateOverrideException();
        }

        boolean overlaps = !rateOverrideRepository.findOverlapping(request.rateType().name(), request.guestCount(),
                request.startDate(), request.endDate(), excludeId).isEmpty();

        if (overlaps) {
            throw new RateOverrideOverlapException();
        }
    }

    @Transactional
    public RateOverride createRateOverride(CreateRateOverrideRequest request) {
        validate(request, null);

        LocalDateTime now = LocalDateTime.now();

        RateOverride saved = rateOverrideRepository.save(new RateOverride(0, request.rateType().name(), request.guestCount(),
                request.startDate(), request.endDate(), request.amount(), request.label(), now, now));

        reconcileAffectedReservations(saved.rateType(), saved.guestCount(), saved.startDate(), saved.endDate());

        return saved;
    }

    @Transactional
    public RateOverride updateRateOverride(int id, CreateRateOverrideRequest request) {
        RateOverride existing = rateOverrideRepository.findById(id)
                                                       .orElseThrow(RateOverrideNotFoundException::new);

        validate(request, id);

        RateOverride saved = rateOverrideRepository.save(new RateOverride(id, request.rateType().name(), request.guestCount(),
                request.startDate(), request.endDate(), request.amount(), request.label(), existing.createdAt(),
                LocalDateTime.now()));

        // Reconcile against both the old and new rateType/guestCount/date range - either one may
        // have reservations whose already-posted charges need to move now that this override's
        // shape has changed.
        reconcileAffectedReservations(existing.rateType(), existing.guestCount(), existing.startDate(), existing.endDate());
        reconcileAffectedReservations(saved.rateType(), saved.guestCount(), saved.startDate(), saved.endDate());

        return saved;
    }

    @Transactional
    public void deleteRateOverride(int id) {
        RateOverride existing = rateOverrideRepository.findById(id).orElseThrow(RateOverrideNotFoundException::new);
        rateOverrideRepository.deleteById(id);

        // Delete first so the reconciliation below recomputes rates with this override already
        // gone, same as resolveNightlyRateAmount() would see it from here on.
        reconcileAffectedReservations(existing.rateType(), existing.guestCount(), existing.startDate(), existing.endDate());
    }

    /**
     * One-time-use backfill for reservations affected by overrides created/changed before this
     * reconciliation wiring existed (createRateOverride/updateRateOverride/deleteRateOverride
     * only reconcile at the moment *they're* called - an override already sitting in the table
     * from before this existed never got its affected reservations reconciled). Safe to call
     * repeatedly: reconcileRoomCharges() always recomputes from current state and only posts a
     * correction when there's an actual delta.
     */
    @Transactional
    public int reconcileAllActiveOverrides() {
        int checked = 0;

        for (RateOverride override : rateOverrideRepository.findAll()) {
            checked += reconcileAffectedReservations(override.rateType(), override.guestCount(),
                    override.startDate(), override.endDate());
        }

        return checked;
    }

    private int reconcileAffectedReservations(String rateType, int guestCount, LocalDate startDate, LocalDate endDate) {
        List<Reservation> candidates = reservationRepository
                .findActiveByRateTypeAndGuestCountOverlapping(rateType, guestCount, startDate, endDate);

        candidates.forEach(reservationService::reconcileRoomCharges);

        return candidates.size();
    }
}
