package com.staydesk.controller;

import com.staydesk.model.Guest;
import com.staydesk.repository.GuestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/guests")
public class GuestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuestController.class);

    private final GuestRepository guestRepository;

    public GuestController(GuestRepository guestRepository) {
        this.guestRepository = guestRepository;
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

        Guest savedGuest = new Guest(0, guest.firstName(), guest.lastName(), guest.email(), guest.phoneNumber(), now, now);
        Guest saved = guestRepository.save(savedGuest);
        URI location = URI.create("/guests/" + saved.id());
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("{id}")
    public ResponseEntity<Guest> updateGuest(@PathVariable Integer id, @RequestBody Guest guest) {
        LOGGER.info("Updating guest {}", guest);

        if (!guestRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Guest updatedGuest = new Guest(guest.id(), guest.firstName(), guest.lastName(), guest.email(), guest.phoneNumber(), guest.createdAt(), LocalDateTime.now());

        return ResponseEntity.ok(guestRepository.save(updatedGuest));
    }
}
