package com.staydesk.controller;

import com.staydesk.exception.PetFriendlyRequiredException;
import com.staydesk.exception.RoomTypeNotFoundException;
import com.staydesk.model.EncryptedString;
import com.staydesk.model.Extra;
import com.staydesk.model.Guest;
import com.staydesk.model.Rate;
import com.staydesk.model.Reservation;
import com.staydesk.model.RoomType;
import com.staydesk.model.dto.BillingAddress;
import com.staydesk.model.dto.ReservationEstimateResponse;
import com.staydesk.model.request.PublicCreateBookingRequest;
import com.staydesk.repository.ExtraRepository;
import com.staydesk.repository.GuestRepository;
import com.staydesk.repository.RateRepository;
import com.staydesk.repository.RoomTypeAvailabilityRepository;
import com.staydesk.repository.RoomTypeRepository;
import com.staydesk.security.PiiCipher;
import com.staydesk.security.PublicEndpointRateLimiter;
import com.staydesk.service.FolioService;
import com.staydesk.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unauthenticated, guest-facing endpoints for the customer booking site (#69). Deliberately a
 * separate, narrower surface from the staff RoomTypeController/RateController/GuestController/
 * ReservationController -- those expose full internal CRUD and guest PII to any staff JWT, which
 * is not appropriate for a public, unauthenticated caller.
 */
@RestController
@RequestMapping("/public")
public class PublicBookingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicBookingController.class);

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeAvailabilityRepository roomTypeAvailabilityRepository;
    private final RateRepository rateRepository;
    private final ExtraRepository extraRepository;
    private final ReservationService reservationService;
    private final GuestRepository guestRepository;
    private final PiiCipher piiCipher;
    private final PublicEndpointRateLimiter rateLimiter;

    public PublicBookingController(RoomTypeRepository roomTypeRepository,
                                   RoomTypeAvailabilityRepository roomTypeAvailabilityRepository,
                                   RateRepository rateRepository, ExtraRepository extraRepository,
                                   ReservationService reservationService, GuestRepository guestRepository,
                                   PiiCipher piiCipher, PublicEndpointRateLimiter rateLimiter) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomTypeAvailabilityRepository = roomTypeAvailabilityRepository;
        this.rateRepository = rateRepository;
        this.extraRepository = extraRepository;
        this.reservationService = reservationService;
        this.guestRepository = guestRepository;
        this.piiCipher = piiCipher;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/room-types")
    public List<RoomType> getRoomTypes() {
        return roomTypeRepository.findAllWithAtLeastOneRoom();
    }

    @GetMapping("/rates")
    public List<Rate> getRates() {
        return rateRepository.findAll();
    }

    @GetMapping("/room-types/{id}/occupied-dates")
    public List<LocalDate> getOccupiedDates(@PathVariable int id) {
        return roomTypeAvailabilityRepository.getFullyBookedDates(id, null);
    }

    /**
     * Pet-fee-style add-ons are only offered when the selected room type is pet-friendly --
     * everything else is offered regardless of room type.
     */
    @GetMapping("/add-ons")
    public List<Extra> getAddOns(@RequestParam int roomTypeId) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId).orElseThrow(RoomTypeNotFoundException::new);

        return extraRepository.findAll().stream()
                              .filter(Extra::active)
                              .filter(extra -> !extra.petFriendlyOnly() || roomType.petFriendly())
                              .toList();
    }

    @GetMapping("/estimate")
    public ReservationEstimateResponse getEstimate(@RequestParam Rate.RateType rateType, @RequestParam int guestCount,
                                                    @RequestParam LocalDate checkInDate, @RequestParam LocalDate checkOutDate,
                                                    @RequestParam int roomTypeId,
                                                    @RequestParam(required = false) List<Integer> addOnIds) {
        List<FolioService.ExtraSelection> extraSelections = resolveAddOnSelections(roomTypeId, addOnIds);
        return reservationService.estimateTotalWithExtras(rateType, guestCount, checkInDate, checkOutDate, null, extraSelections);
    }

    @PostMapping("/bookings")
    @Transactional
    public ResponseEntity<Reservation> createBooking(@Valid @RequestBody PublicCreateBookingRequest request,
                                                      HttpServletRequest httpRequest) {
        if (request.website() != null && !request.website().isBlank()) {
            LOGGER.warn("Rejected public booking submission with a filled honeypot field");
            return ResponseEntity.badRequest().build();
        }

        String clientIp = resolveClientIp(httpRequest);

        if (!rateLimiter.tryAcquire(clientIp)) {
            LOGGER.warn("Rate limit exceeded for public booking submissions from {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        List<FolioService.ExtraSelection> extraSelections = resolveAddOnSelections(request.roomTypeId(), request.addOnIds());

        Guest guest = findOrCreateGuest(request);
        LocalDateTime now = LocalDateTime.now();

        BillingAddress billingAddress = new BillingAddress(request.billingStreet(), request.billingStreet2(),
                request.billingCity(), request.billingState(), request.billingZip());

        Reservation savedReservation = reservationService.createReservation(
                new Reservation(0, 0, guest.id(), null, request.roomTypeId(), request.checkInDate(), request.checkOutDate(),
                        Reservation.ReservationStatus.CONFIRMED, null, null, request.rateType(), request.guestCount(),
                        Reservation.Channel.ONLINE, false, now, now, null, request.specialRequests()),
                request.paymentToken(), extraSelections, billingAddress);

        URI location = URI.create("/public/bookings/" + savedReservation.id());
        return ResponseEntity.created(location).body(savedReservation);
    }

    private List<FolioService.ExtraSelection> resolveAddOnSelections(int roomTypeId, List<Integer> addOnIds) {
        if (addOnIds == null || addOnIds.isEmpty()) {
            return List.of();
        }

        RoomType roomType = roomTypeRepository.findById(roomTypeId).orElseThrow(RoomTypeNotFoundException::new);
        List<Extra> extras = extraRepository.findAllById(addOnIds);

        for (Extra extra : extras) {
            if (extra.petFriendlyOnly() && !roomType.petFriendly()) {
                throw new PetFriendlyRequiredException();
            }
        }

        return addOnIds.stream().map(id -> new FolioService.ExtraSelection(id, 1)).toList();
    }

    private Guest findOrCreateGuest(PublicCreateBookingRequest request) {
        String emailHash = piiCipher.hash(request.email().strip().toLowerCase());

        return guestRepository.findByEmailHash(emailHash).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();

            Guest saved = guestRepository.save(new Guest(0, new EncryptedString(request.firstName()),
                    new EncryptedString(request.lastName()), new EncryptedString(request.email()), emailHash,
                    new EncryptedString(request.phoneNumber()), request.smsConsent(), false, null, null, null,
                    false, false, null, Rate.RateType.NIGHTLY, false, Guest.GuestType.INDIVIDUAL, now, now));

            return saved;
        });
    }

    private String resolveClientIp(HttpServletRequest request) {
        // The first entry in X-Forwarded-For is client-controllable (a caller can prepend anything);
        // the LAST entry is the one appended by our own reverse proxy and is what can be trusted.
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            return parts[parts.length - 1].strip();
        }

        return request.getRemoteAddr();
    }
}
