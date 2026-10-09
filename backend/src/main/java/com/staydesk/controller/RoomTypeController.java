package com.staydesk.controller;

import com.staydesk.model.RoomType;
import com.staydesk.model.request.UpdateRoomTypeRequest;
import com.staydesk.repository.RoomTypeAvailabilityRepository;
import com.staydesk.repository.RoomTypeRepository;
import com.staydesk.service.ReservationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/room-types")
public class RoomTypeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomTypeController.class);

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeAvailabilityRepository roomTypeAvailabilityRepository;
    private final ReservationService reservationService;

    public RoomTypeController(RoomTypeRepository roomTypeRepository, RoomTypeAvailabilityRepository roomTypeAvailabilityRepository,
                              ReservationService reservationService) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomTypeAvailabilityRepository = roomTypeAvailabilityRepository;
        this.reservationService = reservationService;
    }

    @GetMapping
    public List<RoomType> getRoomTypes(@RequestParam(defaultValue = "false") boolean includeEmpty) {
        LOGGER.info("Getting all room types");
        // A room type with no rooms assigned yet (e.g. staff pre-created it ahead of converting an
        // existing room to it) isn't a real booking option - hide it everywhere except the screens
        // staff actually need it for: Settings (managing room types) and the Room form (assigning a
        // room to it in the first place).
        return includeEmpty ? roomTypeRepository.findAll() : roomTypeRepository.findAllWithAtLeastOneRoom();
    }

    @GetMapping("{id}/occupied-dates")
    public List<LocalDate> getFullyBookedDates(@PathVariable int id,
                                               @RequestParam(required = false) Integer excludeReservationId) {
        return roomTypeAvailabilityRepository.getFullyBookedDates(id, excludeReservationId);
    }

    @GetMapping("availability")
    public List<Integer> getUnavailableRoomTypeIds(@RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut,
                                                    @RequestParam(required = false) Integer excludeReservationId) {
        return reservationService.getUnavailableRoomTypeIds(checkIn, checkOut, excludeReservationId);
    }

    @PutMapping("{id}")
    public ResponseEntity<RoomType> updateRoomType(@PathVariable int id, @Valid @RequestBody UpdateRoomTypeRequest request) {
        LOGGER.info("Updating room type {}", id);

        RoomType existing = roomTypeRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        return roomTypeRepository.findByName(request.name())
                                  .filter(other -> other.id() != id)
                                  .<ResponseEntity<RoomType>>map(other -> ResponseEntity.status(HttpStatus.CONFLICT).build())
                                  .orElseGet(() -> {
                                      RoomType updated = new RoomType(id, request.name(), existing.availableCount(),
                                              existing.unavailableCount(), existing.createdAt(), LocalDateTime.now());
                                      return ResponseEntity.ok(roomTypeRepository.save(updated));
                                  });
    }
}
