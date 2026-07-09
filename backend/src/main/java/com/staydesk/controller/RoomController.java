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
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomController.class);

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
        return roomRepository.findAll();
    }

    @GetMapping("{id}")
    public ResponseEntity<Room> getRoom(@PathVariable Integer id) {
        LOGGER.info("Finding room by id {}", id);

        return roomRepository.findById(id)
                             .map(ResponseEntity::ok)
                             .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody Room room) {
        LOGGER.info("Saving room {}", room);
        LocalDateTime now = LocalDateTime.now();
        Room savedRoom = new Room(0, room.roomNumber(), room.roomTypeId(), room.status(), room.sifelyLockId(), now, now);
        Room saved = roomRepository.save(savedRoom);
        adjustRoomTypeCount(saved.roomTypeId(), saved.status(), 1);
        URI location = URI.create("/rooms/" + saved.id());
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("{id}")
    public ResponseEntity<Room> updateRoom(@PathVariable Integer id, @RequestBody Room room) {
        LOGGER.info("Updating room {}", room);

        Room existing = roomRepository.findById(id).orElse(null);

        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        Room updatedRoom = new Room(id, room.roomNumber(), room.roomTypeId(), room.status(), room.sifelyLockId(), room.createdAt(), LocalDateTime.now());
        Room saved = roomRepository.save(updatedRoom);

        if (existing.roomTypeId() != saved.roomTypeId() || existing.status() != saved.status()) {
            adjustRoomTypeCount(existing.roomTypeId(), existing.status(), -1);
            adjustRoomTypeCount(saved.roomTypeId(), saved.status(), 1);
        }

        return ResponseEntity.ok(saved);
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
