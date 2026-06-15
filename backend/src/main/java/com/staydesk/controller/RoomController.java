package com.staydesk.controller;

import com.staydesk.model.Room;
import com.staydesk.repository.RoomRepository;
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

    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
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
        Room savedRoom = new Room(0, room.roomNumber(), room.type(), room.status(), now, now);
        Room saved = roomRepository.save(savedRoom);
        URI location = URI.create("/rooms/" + saved.id());
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("{id}")
    public ResponseEntity<Room> updateRoom(@PathVariable Integer id, @RequestBody Room room) {
        LOGGER.info("Updating room {}", room);

        if (checkRoomDoesNotExist(id)) {
            return ResponseEntity.notFound().build();
        }

        Room updatedRoom = new Room(id, room.roomNumber(), room.type(), room.status(), room.createdAt(), LocalDateTime.now());

        return ResponseEntity.ok(roomRepository.save(updatedRoom));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Integer id) {
        LOGGER.info("Deleting room {}", id);

        if (checkRoomDoesNotExist(id)) {
            return ResponseEntity.notFound().build();
        }

        roomRepository.deleteById(id);

        return ResponseEntity.noContent().build();
    }

    private boolean checkRoomDoesNotExist(Integer id) {
        if (!roomRepository.existsById(id)) {
            LOGGER.warn("Room with id {} does not exist", id);
            return true;
        }

        return false;
    }
}
