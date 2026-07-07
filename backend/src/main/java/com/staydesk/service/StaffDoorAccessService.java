package com.staydesk.service;

import com.staydesk.model.Employee;
import com.staydesk.model.Room;
import com.staydesk.model.StaffLockPasscode;
import com.staydesk.repository.RoomRepository;
import com.staydesk.repository.StaffLockPasscodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StaffDoorAccessService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffDoorAccessService.class);

    private final SifelyLockService sifelyLockService;
    private final RoomRepository roomRepository;
    private final StaffLockPasscodeRepository staffLockPasscodeRepository;

    public StaffDoorAccessService(SifelyLockService sifelyLockService, RoomRepository roomRepository,
                                  StaffLockPasscodeRepository staffLockPasscodeRepository) {
        this.sifelyLockService = sifelyLockService;
        this.roomRepository = roomRepository;
        this.staffLockPasscodeRepository = staffLockPasscodeRepository;
    }

    @Async
    public void grantAccess(Employee employee, String pin) {
        try {
            GrantResult result = issueToRooms(employee, pin, lockedRooms());
            logIfPartialFailure(employee, result);
        } catch (Exception e) {
            LOGGER.error("Unexpected failure granting door access for employee {}", employee.id(), e);
        }
    }

    @Async
    public void syncAccessIfNeeded(Employee employee, String pin) {
        if (!employee.doorAccessEnabled() || !PasscodeRules.isValidPin(pin)) {
            return;
        }

        try {
            Set<Long> alreadyGranted = staffLockPasscodeRepository.findByEmployeeIdAndStatus(employee.id(), StaffLockPasscode.Status.ACTIVE)
                                                                  .stream()
                                                                  .map(StaffLockPasscode::lockId)
                                                                  .collect(Collectors.toSet());

            List<Room> missingRooms = lockedRooms().stream()
                                                   .filter(r -> !alreadyGranted.contains(r.sifelyLockId()))
                                                   .toList();

            if (!missingRooms.isEmpty()) {
                GrantResult result = issueToRooms(employee, pin, missingRooms);
                LOGGER.info("Login-time door access sync for employee {}: {} backfilled, {} failed",
                        employee.id(), result.succeeded(), result.failedRoomNumbers().size());
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected failure during login-time door access sync for employee {}", employee.id(), e);
        }
    }

    public void revokeAccess(Employee employee) {
        staffLockPasscodeRepository.findByEmployeeIdAndStatus(employee.id(), StaffLockPasscode.Status.ACTIVE)
                                   .forEach(sp -> {
                                       try {
                                           sifelyLockService.deletePasscode(sp.lockId(), sp.keyboardPwdId());
                                       } catch (Exception e) {
                                           LOGGER.error("Failed to revoke staff passcode {} for employee {} - marking revoked anyway",
                                                   sp.keyboardPwdId(), employee.id(), e);
                                       }

                                       staffLockPasscodeRepository.save(new StaffLockPasscode(sp.id(), sp.employeeId(), sp.lockId(),
                                               sp.keyboardPwdId(), StaffLockPasscode.Status.REVOKED, sp.createdAt(), LocalDateTime.now()));
                                   });
    }

    private List<Room> lockedRooms() {
        return roomRepository.findAll().stream()
                             .filter(r -> r.sifelyLockId() != null)
                             .toList();
    }

    private GrantResult issueToRooms(Employee employee, String pin, List<Room> rooms) {
        int succeeded = 0;
        List<Integer> failedRoomNumbers = new ArrayList<>();

        for (Room room : rooms) {
            try {
                String keyboardPwdId = sifelyLockService.createPermanentPasscode(room.sifelyLockId(), pin, PasscodeLabels.forStaff(employee.username()));

                staffLockPasscodeRepository.save(new StaffLockPasscode(0, employee.id(), room.sifelyLockId(),
                        Long.parseLong(keyboardPwdId), StaffLockPasscode.Status.ACTIVE, LocalDateTime.now(), null));
                succeeded++;
            } catch (Exception e) {
                LOGGER.error("Failed to issue staff passcode for employee {} on lock {}", employee.id(), room.sifelyLockId(), e);
                failedRoomNumbers.add(room.roomNumber());
            }
        }

        return new GrantResult(succeeded, failedRoomNumbers);
    }

    private void logIfPartialFailure(Employee employee, GrantResult result) {
        if (!result.failedRoomNumbers().isEmpty()) {
            LOGGER.warn("Door access partially failed for employee {}: rooms {}", employee.id(), result.failedRoomNumbers());
        }
    }

    public record GrantResult(int succeeded, List<Integer> failedRoomNumbers) {
    }
}