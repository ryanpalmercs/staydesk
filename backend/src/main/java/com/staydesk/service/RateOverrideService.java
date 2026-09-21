package com.staydesk.service;

import com.staydesk.exception.InvalidRateOverrideException;
import com.staydesk.exception.RateOverrideNotFoundException;
import com.staydesk.exception.RateOverrideOverlapException;
import com.staydesk.model.Rate;
import com.staydesk.model.RateOverride;
import com.staydesk.model.request.CreateRateOverrideRequest;
import com.staydesk.repository.RateOverrideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RateOverrideService {

    private final RateOverrideRepository rateOverrideRepository;

    public RateOverrideService(RateOverrideRepository rateOverrideRepository) {
        this.rateOverrideRepository = rateOverrideRepository;
    }

    public List<RateOverride> getRateOverrides() {
        return rateOverrideRepository.findAll();
    }

    private void validate(CreateRateOverrideRequest request, Integer excludeId) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new InvalidRateOverrideException();
        }

        if (request.rateType() != Rate.RateType.NIGHTLY) {
            throw new InvalidRateOverrideException("Rate overrides are only supported for the NIGHTLY rate type.");
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

        return rateOverrideRepository.save(new RateOverride(0, request.rateType().name(), request.guestCount(),
                request.startDate(), request.endDate(), request.amount(), request.label(), now, now));
    }

    @Transactional
    public RateOverride updateRateOverride(int id, CreateRateOverrideRequest request) {
        RateOverride existing = rateOverrideRepository.findById(id)
                                                       .orElseThrow(RateOverrideNotFoundException::new);

        validate(request, id);

        return rateOverrideRepository.save(new RateOverride(id, request.rateType().name(), request.guestCount(),
                request.startDate(), request.endDate(), request.amount(), request.label(), existing.createdAt(),
                LocalDateTime.now()));
    }

    @Transactional
    public void deleteRateOverride(int id) {
        rateOverrideRepository.findById(id).orElseThrow(RateOverrideNotFoundException::new);
        rateOverrideRepository.deleteById(id);
    }
}
