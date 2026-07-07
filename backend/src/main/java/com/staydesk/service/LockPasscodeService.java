package com.staydesk.service;

import com.staydesk.model.LockPasscode;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.repository.LockPasscodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class LockPasscodeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LockPasscodeService.class);
    private static final ZoneId PROPERTY_ZONE = ZoneId.of("America/Chicago");
    private static final LocalTime CHECKOUT_TIME = LocalTime.of(11, 0);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SifelyLockService sifelyLockService;
    private final LockPasscodeRepository lockPasscodeRepository;

    public LockPasscodeService(SifelyLockService sifelyLockService, LockPasscodeRepository lockPasscodeRepository) {
        this.sifelyLockService = sifelyLockService;
        this.lockPasscodeRepository = lockPasscodeRepository;
    }

    public Optional<String> issuePasscode(Reservation reservation, Room room) {
        if (room.sifelyLockId() == null) {
            LOGGER.info("Room {} has no Sifely lock assigned, skipping passcode issuance", room.roomNumber());
            return Optional.empty();
        }

        long lockId = room.sifelyLockId();
        String passcode = generatePasscode();

        LocalDateTime startDate = LocalDateTime.now(PROPERTY_ZONE);
        LocalDateTime endDate = LocalDateTime.of(reservation.checkOutDate(), CHECKOUT_TIME);

        try {
            String keyboardPwdId = sifelyLockService.createPasscode(
                    lockId, passcode, "Res-" + reservation.id(),
                    startDate.atZone(PROPERTY_ZONE).toInstant().toEpochMilli(),
                    endDate.atZone(PROPERTY_ZONE).toInstant().toEpochMilli()
            );

            lockPasscodeRepository.save(new LockPasscode(0, reservation.id(), lockId, Long.parseLong(keyboardPwdId),
                    passcode, startDate, endDate, LockPasscode.Status.ACTIVE, LocalDateTime.now(), null));

            LOGGER.info("Issued Sifely passcode for reservation {} on lock {}", reservation.id(), lockId);
            return Optional.of(passcode);
        } catch (Exception e) {
            LOGGER.error("Failed to issue Sifely passcode for reservation {} on lock {} - continuing without one",
                    reservation.id(), lockId, e);
            return Optional.empty();
        }
    }

    public void revokePasscodes(int reservationId) {
        lockPasscodeRepository.findByReservationIdAndStatus(reservationId, LockPasscode.Status.ACTIVE)
                              .forEach(lockPasscode -> {
                                  try {
                                      sifelyLockService.deletePasscode(lockPasscode.lockId(), lockPasscode.keyboardPwdId());
                                  } catch (Exception e) {
                                      LOGGER.error("Failed to revoke Sifely passcode {} for reservation {} - marking revoked anyway",
                                              lockPasscode.keyboardPwdId(), reservationId, e);
                                  }

                                  lockPasscodeRepository.save(new LockPasscode(lockPasscode.id(), lockPasscode.reservationId(),
                                          lockPasscode.lockId(), lockPasscode.keyboardPwdId(), lockPasscode.passcode(),
                                          lockPasscode.startDate(), lockPasscode.endDate(), LockPasscode.Status.REVOKED,
                                          lockPasscode.createdAt(), LocalDateTime.now()));
                              });
    }

    private String generatePasscode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = String.valueOf(1000 + RANDOM.nextInt(9000));

            if (!isConsecutiveOrRepeated(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a valid passcode after 20 attempts");
    }

    private boolean isConsecutiveOrRepeated(String code) {
        boolean ascending = true;
        boolean descending = true;
        boolean repeated = true;

        for (int i = 1; i < code.length(); i++) {
            int prev = code.charAt(i - 1) - '0';
            int curr = code.charAt(i) - '0';

            if (curr != prev + 1) {
                ascending = false;
            }
            if (curr != prev - 1) {
                descending = false;
            }
            if (curr != prev) {
                repeated = false;
            }
        }

        return ascending || descending || repeated;
    }
}