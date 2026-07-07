package com.staydesk.service;

import com.staydesk.model.AuthRole;
import com.staydesk.model.ContactInfo;
import com.staydesk.model.Employee;
import com.staydesk.model.LockPasscode;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.model.TimeEntry;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.EmployeeTypeRepository;
import com.staydesk.repository.LockPasscodeRepository;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.RoomRepository;
import com.staydesk.repository.TimeEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class LockPasscodeRetryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LockPasscodeRetryService.class);
    private static final ZoneId PROPERTY_ZONE = ZoneId.of("America/Chicago");

    private final LockPasscodeRepository lockPasscodeRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeTypeRepository employeeTypeRepository;
    private final SifelyLockService sifelyLockService;
    private final SmsService smsService;

    public LockPasscodeRetryService(LockPasscodeRepository lockPasscodeRepository,
                                    ReservationRepository reservationRepository,
                                    RoomRepository roomRepository, TimeEntryRepository timeEntryRepository,
                                    EmployeeRepository employeeRepository,
                                    EmployeeTypeRepository employeeTypeRepository, SifelyLockService sifelyLockService,
                                    SmsService smsService) {
        this.lockPasscodeRepository = lockPasscodeRepository;
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.employeeRepository = employeeRepository;
        this.employeeTypeRepository = employeeTypeRepository;
        this.sifelyLockService = sifelyLockService;
        this.smsService = smsService;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void retryFailedPasscodes() {
        List<LockPasscode> failedPasscodes = lockPasscodeRepository.findByStatus(LockPasscode.Status.FAILED);

        if (failedPasscodes.isEmpty()) {
            return;
        }

        List<String> onShiftPhoneNumbers = getOnShiftFrontDeskPhoneNumbers();
        failedPasscodes.forEach(failed -> retry(failed, onShiftPhoneNumbers));
    }

    private void retry(LockPasscode failed, List<String> onShiftPhoneNumbers) {
        Reservation reservation = reservationRepository.findById(failed.reservationId()).orElse(null);

        if (reservation == null || reservation.status() != Reservation.ReservationStatus.CHECKED_IN) {
            lockPasscodeRepository.save(failed.withStatus(LockPasscode.Status.EXPIRED).withResolvedAt(LocalDateTime.now()));
            return;
        }

        String keyboardPwdId;

        try {
            keyboardPwdId = sifelyLockService.createPasscode(
                    failed.lockId(), failed.passcode(), PasscodeLabels.forReservation(failed.reservationId()),
                    failed.startDate().atZone(PROPERTY_ZONE).toInstant().toEpochMilli(),
                    failed.endDate().atZone(PROPERTY_ZONE).toInstant().toEpochMilli(),
                    3
            );
        } catch (Exception e) {
            LOGGER.warn("Background retry still failing for reservation {} on lock {}: {}",
                    failed.reservationId(), failed.lockId(), e.getMessage());
            return;
        }

        LockPasscode resolved = failed.withStatus(LockPasscode.Status.ACTIVE)
                                      .withKeyboardPwdId(Long.parseLong(keyboardPwdId))
                                      .withAcknowledged(false)
                                      .withResolvedAt(LocalDateTime.now());

        lockPasscodeRepository.save(resolved);

        LOGGER.info("Background retry succeeded for reservation {} on lock {}", failed.reservationId(), failed.lockId());

        try {
            int roomNumber = roomRepository.findById(reservation.roomId()).map(Room::roomNumber).orElse(0);

            if (onShiftPhoneNumbers.isEmpty()) {
                LOGGER.warn("No front-desk-capable employee currently clocked in - room {} door code notification relying on in-app toast only", roomNumber);
            } else {
                onShiftPhoneNumbers.forEach(phone -> smsService.sendDoorCodeResolved(phone, roomNumber));
            }
        } catch (Exception e) {
            LOGGER.error("Passcode for reservation {} was issued successfully, but notifying staff failed - relying on in-app toast only",
                    failed.reservationId(), e);
        }
    }

    private List<String> getOnShiftFrontDeskPhoneNumbers() {
        return timeEntryRepository.getAllOpenEntries().stream()
                                  .map(TimeEntry::employeeId)
                                  .distinct()
                                  .map(employeeRepository::findById)
                                  .flatMap(Optional::stream)
                                  .filter(this::isFrontDeskCapable)
                                  .map(Employee::contactInfo)
                                  .filter(Objects::nonNull)
                                  .map(ContactInfo::phone)
                                  .filter(phone -> phone != null && !phone.isBlank())
                                  .toList();
    }

    private boolean isFrontDeskCapable(Employee employee) {
        return employeeTypeRepository.findById(employee.employeeTypeId())
                                     .map(type -> type.authRole() == AuthRole.ADMIN
                                                  || type.authRole() == AuthRole.MANAGER
                                                  || type.authRole() == AuthRole.FRONT_DESK)
                                     .orElse(false);
    }
}