package com.staydesk.controller;

import com.staydesk.model.LockPasscode;
import com.staydesk.model.dto.UnacknowledgedDoorAccessNotification;
import com.staydesk.repository.LockPasscodeRepository;
import com.staydesk.service.LockPasscodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/lock-passcodes")
public class LockPasscodeController {
    private final LockPasscodeService lockPasscodeService;
    private final LockPasscodeRepository lockPasscodeRepository;

    public LockPasscodeController(LockPasscodeService lockPasscodeService,
                                  LockPasscodeRepository lockPasscodeRepository) {
        this.lockPasscodeService = lockPasscodeService;
        this.lockPasscodeRepository = lockPasscodeRepository;
    }

    @GetMapping("unacknowledged")
    public List<UnacknowledgedDoorAccessNotification> getUnacknowledged() {
        return lockPasscodeService.getUnacknowledgedNotifications();
    }

    @GetMapping("reservation/{reservationId}")
    public ResponseEntity<LockPasscode> getActiveForReservation(@PathVariable int reservationId) {
        return lockPasscodeRepository.findByReservationIdAndStatus(reservationId, LockPasscode.Status.ACTIVE)
                                      .stream().findFirst()
                                      .map(ResponseEntity::ok)
                                      .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable int id) {
        lockPasscodeRepository.acknowledge(id);
        return ResponseEntity.noContent().build();
    }
}