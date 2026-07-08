package com.staydesk.controller;

import com.staydesk.model.Room;
import com.staydesk.model.dto.RoomAccessEvent;
import com.staydesk.repository.RoomRepository;
import com.staydesk.service.RoomAccessLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/rooms")
public class AdminRoomController {
    private final RoomRepository roomRepository;
    private final RoomAccessLogService roomAccessLogService;

    public AdminRoomController(RoomRepository roomRepository, RoomAccessLogService roomAccessLogService) {
        this.roomRepository = roomRepository;
        this.roomAccessLogService = roomAccessLogService;
    }

    @GetMapping("{id}/access-log")
    public ResponseEntity<List<RoomAccessEvent>> getAccessLog(@PathVariable Integer id,
                                                              @RequestParam(defaultValue = "90") int days) {
        Room room = roomRepository.findById(id).orElse(null);

        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        if (room.sifelyLockId() == null) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(roomAccessLogService.getAccessLog(room.sifelyLockId(), days));
    }
}