package com.staydesk.controller;

import com.staydesk.model.Room;
import com.staydesk.model.dto.OccupiedRangeDto;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.RoomRepository;
import com.staydesk.repository.RoomTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomController.class);
    private static final ZoneId PROPERTY_ZONE = ZoneId.of("America/Chicago");

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final ReservationRepository reservationRepository;

    public RoomController(RoomRepository roomRepository, RoomTypeRepository roomTypeRepository,
                          ReservationRepository reservationRepository) {
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.reservationRepository = reservationRepository;
    }

    @GetMapping
    public List<Room> getRooms() {
        LOGGER.info("Finding all rooms");
        return roomRepository.findAllWithComputedStatus(LocalDate.now(PROPERTY_ZONE));
    }

    @GetMapping("{id}")
    public ResponseEntity<Room> getRoom(@PathVariable Integer id) {
        LOGGER.info("Finding room by id {}", id);

        return roomRepository.findByIdWithComputedStatus(id, LocalDate.now(PROPERTY_ZONE))
                             .map(ResponseEntity::ok)
                             .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody Room room) {
        LOGGER.info("Saving room {}", room);
        LocalDateTime now = LocalDateTime.now();
        Room savedRoom = new Room(0, room.roomNumber(), room.roomTypeId(), normalizeStoredStatus(room.status()), room.sifelyLockId(), room.maintenanceNote(), now, now);
        Room saved = roomRepository.save(savedRoom);
        adjustRoomTypeCount(saved.roomTypeId(), saved.status(), 1);
        URI location = URI.create("/rooms/" + saved.id());
        return ResponseEntity.created(location).body(saved);
    }

    /**
     * Partial update: a key absent from the request body leaves that field untouched, so a client
     * only needs to send the fields it actually changed rather than the whole room (a round-tripped
     * field the client merely echoed back - like a live-computed status - can never overwrite
     * anything, since it was never actually "changed"). A key present with a null value (e.g.
     * clearing maintenanceNote or unassigning sifelyLockId) is a deliberate clear, which a plain
     * null-means-unset DTO couldn't distinguish from "not sent".
     */
    @PutMapping("{id}")
    public ResponseEntity<Room> updateRoom(@PathVariable Integer id, @RequestBody Map<String, Object> updates) {
        LOGGER.info("Updating room {} with {}", id, updates);

        Room existing = roomRepository.findById(id).orElse(null);

        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        int roomNumber = updates.containsKey("roomNumber") ? ((Number) updates.get("roomNumber")).intValue() : existing.roomNumber();
        int roomTypeId = updates.containsKey("roomTypeId") ? ((Number) updates.get("roomTypeId")).intValue() : existing.roomTypeId();
        Room.RoomStatus status = updates.containsKey("status")
                ? normalizeStoredStatus(Room.RoomStatus.valueOf((String) updates.get("status")))
                : existing.status();
        Long sifelyLockId = updates.containsKey("sifelyLockId")
                ? (updates.get("sifelyLockId") == null ? null : ((Number) updates.get("sifelyLockId")).longValue())
                : existing.sifelyLockId();
        String maintenanceNote = updates.containsKey("maintenanceNote") ? (String) updates.get("maintenanceNote") : existing.maintenanceNote();

        Room updatedRoom = new Room(id, roomNumber, roomTypeId, status, sifelyLockId, maintenanceNote, existing.createdAt(), LocalDateTime.now());
        Room saved = roomRepository.save(updatedRoom);

        if (existing.roomTypeId() != saved.roomTypeId() || existing.status() != saved.status()) {
            adjustRoomTypeCount(existing.roomTypeId(), existing.status(), -1);
            adjustRoomTypeCount(saved.roomTypeId(), saved.status(), 1);
        }

        return ResponseEntity.ok(saved);
    }

    /**
     * OCCUPIED is never a value that gets persisted here - it's computed live from reservations
     * (see RoomRepository.findAllWithComputedStatus/findByIdWithComputedStatus). Without this, an
     * edit form that loaded a room's live-computed OCCUPIED status and got saved for an unrelated
     * field (door lock, room number) would round-trip that value back into the stored column and
     * freeze it there, since nothing else ever writes it back.
     */
    private static Room.RoomStatus normalizeStoredStatus(Room.RoomStatus status) {
        return status == Room.RoomStatus.OCCUPIED ? Room.RoomStatus.AVAILABLE : status;
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Integer id) {
        LOGGER.info("Deleting room {}", id);

        Room existing = roomRepository.findById(id).orElse(null);

        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        roomRepository.deleteById(id);
        adjustRoomTypeCount(existing.roomTypeId(), existing.status(), -1);

        return ResponseEntity.noContent().build();
    }

    private void adjustRoomTypeCount(int roomTypeId, Room.RoomStatus status, int delta) {
        if (status == Room.RoomStatus.MAINTENANCE) {
            if (delta > 0) {
                roomTypeRepository.incrementUnavailableCount(roomTypeId);
            } else {
                roomTypeRepository.decrementUnavailableCount(roomTypeId);
            }
        } else {
            if (delta > 0) {
                roomTypeRepository.incrementAvailableCount(roomTypeId);
            } else {
                roomTypeRepository.decrementAvailableCount(roomTypeId);
            }
        }
    }

    @GetMapping("{roomId}/occupied-dates")
    public List<OccupiedRangeDto> getOccupiedDates(@PathVariable int roomId) {
        return reservationRepository.findActiveByRoomId(roomId).stream()
                                    .map(r -> new OccupiedRangeDto(r.checkInDate(), r.checkOutDate()))
                                    .toList();
    }
}
