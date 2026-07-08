package com.staydesk.controller;

import com.staydesk.model.Guest;
import com.staydesk.model.request.FlagGuestRequest;
import com.staydesk.repository.GuestRepository;
import com.staydesk.service.GuestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/guests")
public class GuestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuestController.class);

    private final GuestRepository guestRepository;
    private final GuestService guestService;

    public GuestController(GuestRepository guestRepository, GuestService guestService) {
        this.guestRepository = guestRepository;
        this.guestService = guestService;
    }

    @GetMapping
    public List<Guest> getGuests() {
        LOGGER.info("Retrieving all guests");
        return guestRepository.findAll();
    }

    @GetMapping("{id}")
    public ResponseEntity<Guest> getGuestById(@PathVariable Integer id) {
        LOGGER.info("Retrieving guest by id {}", id);
        return guestRepository.findById(id)
                              .map(ResponseEntity::ok)
                              .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Guest> createGuest(@RequestBody Guest guest) {
        LOGGER.info("Creating guest {}", guest);

        if (guestRepository.getGuestByEmail(guest.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        LocalDateTime now = LocalDateTime.now();

        Guest savedGuest = new Guest(0, guest.firstName(), guest.lastName(), guest.email(), guest.phoneNumber(),
                false, null, null, null, false, now, now);
        Guest saved = guestRepository.save(savedGuest);
        URI location = URI.create("/guests/" + saved.id());
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("{id}")
    public ResponseEntity<Guest> updateGuest(@PathVariable Integer id, @RequestBody Guest guest) {
        LOGGER.info("Updating guest {}", guest);

        Guest existing = guestRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        Guest updatedGuest = new Guest(id, guest.firstName(), guest.lastName(), guest.email(), guest.phoneNumber(),
                existing.flagged(), existing.flagReason(), existing.flaggedDate(), existing.flaggedBy(),
                existing.legalHold(), existing.createdAt(), LocalDateTime.now());

        return ResponseEntity.ok(guestRepository.save(updatedGuest));
    }

    @PostMapping("{id}/flag")
    public ResponseEntity<Guest> flagGuest(@PathVariable Integer id, @RequestBody FlagGuestRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        LOGGER.info("Flagging guest {}", id);
        return ResponseEntity.ok(guestService.flagGuest(id, request.reason(), UUID.fromString(jwt.getSubject())));
    }

    @DeleteMapping("{id}/flag")
    public ResponseEntity<Guest> unflagGuest(@PathVariable Integer id) {
        LOGGER.info("Unflagging guest {}", id);
        return ResponseEntity.ok(guestService.unflagGuest(id));
    }

    @PostMapping("{id}/legal-hold")
    public ResponseEntity<Guest> setLegalHold(@PathVariable Integer id) {
        LOGGER.info("Placing legal hold on guest {}", id);
        return ResponseEntity.ok(guestService.setLegalHold(id));
    }

    @DeleteMapping("{id}/legal-hold")
    public ResponseEntity<Guest> clearLegalHold(@PathVariable Integer id) {
        LOGGER.info("Clearing legal hold on guest {}", id);
        return ResponseEntity.ok(guestService.clearLegalHold(id));
    }
}