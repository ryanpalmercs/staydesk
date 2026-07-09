package com.staydesk.service;

import com.staydesk.lock.CodeResult;
import com.staydesk.model.LockPasscode;
import com.staydesk.model.Reservation;
import com.staydesk.model.Room;
import com.staydesk.model.dto.UnacknowledgedDoorAccessNotification;
import com.staydesk.provider.ProviderFactory;
import com.staydesk.repository.LockPasscodeRepository;
import com.staydesk.repository.ReservationRepository;
import com.staydesk.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class LockPasscodeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LockPasscodeService.class);
    private static final ZoneId PROPERTY_ZONE = ZoneId.of("America/Chicago");
    private static final LocalTime CHECKOUT_TIME = LocalTime.of(11, 0);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 1500;

    private final ProviderFactory providerFactory;
    private final LockPasscodeRepository lockPasscodeRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;

    public LockPasscodeService(ProviderFactory providerFactory, LockPasscodeRepository lockPasscodeRepository,
                               ReservationRepository reservationRepository, RoomRepository roomRepository) {
        this.providerFactory = providerFactory;
        this.lockPasscodeRepository = lockPasscodeRepository;
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
    }

    public PasscodeResult issuePasscode(Reservation reservation, Room room) {
        if (room.sifelyLockId() == null) {
            LOGGER.info("Room {} has no Sifely lock assigned, skipping passcode issuance", room.roomNumber());
            return new PasscodeResult(PasscodeResult.Outcome.NO_LOCK_ASSIGNED, null);
        }

        long lockId = room.sifelyLockId();
        String passcode = generatePasscode();
        LocalDateTime startDate = LocalDateTime.now(PROPERTY_ZONE);
        LocalDateTime endDate = LocalDateTime.of(reservation.checkOutDate(), CHECKOUT_TIME);

        Long keyboardPwdId = attemptIssue(reservation, lockId, passcode, startDate, endDate);
        LockPasscode.Status status = keyboardPwdId != null ? LockPasscode.Status.ACTIVE : LockPasscode.Status.FAILED;

        try {
            lockPasscodeRepository.save(new LockPasscode(0, reservation.id(), lockId, keyboardPwdId, passcode,
                    startDate, endDate, status, true, LocalDateTime.now(), null));
        } catch (Exception e) {
            LOGGER.error("Failed to persist lock passcode record for reservation {} on lock {} (Sifely outcome: {})",
                    reservation.id(), lockId, status, e);

            if (keyboardPwdId != null) {
                compensateOrphanedPasscode(reservation, lockId, keyboardPwdId);
            }

            return new PasscodeResult(PasscodeResult.Outcome.FAILED, null);
        }

        if (status == LockPasscode.Status.ACTIVE) {
            LOGGER.info("Issued Sifely passcode for reservation {} on lock {}", reservation.id(), lockId);
            return new PasscodeResult(PasscodeResult.Outcome.ISSUED, passcode);
        }

        return new PasscodeResult(PasscodeResult.Outcome.FAILED, null);
    }

    private void compensateOrphanedPasscode(Reservation reservation, long lockId, long keyboardPwdId) {
        try {
            providerFactory.getLockProvider().revokeCode(String.valueOf(lockId), String.valueOf(keyboardPwdId));
            LOGGER.warn("Compensated: deleted orphaned Sifely passcode {} for reservation {} on lock {} after a DB save failure",
                    keyboardPwdId, reservation.id(), lockId);
        } catch (Exception e) {
            LOGGER.error("CRITICAL: reservation {} has an untracked working passcode {} on lock {} - DB save failed and the compensating service delete also failed. Manual cleanup required.",
                    reservation.id(), keyboardPwdId, lockId, e);
        }
    }

    private Long attemptIssue(Reservation reservation, long lockId, String passcode, LocalDateTime startDate,
                              LocalDateTime endDate) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            CodeResult result = providerFactory.getLockProvider().issueCode(
                    String.valueOf(lockId), passcode, PasscodeLabels.forReservation(reservation.id()),
                    startDate, endDate, null);

            if (result.success()) {
                return Long.parseLong(result.codeId());
            }

            LOGGER.warn("Lock passcode attempt {}/{} failed for reservation {} on lock {}: {}",
                    attempt, MAX_ATTEMPTS, reservation.id(), lockId, result.message());

            if (attempt < MAX_ATTEMPTS) {
                sleep(RETRY_DELAY_MILLIS);
            }
        }

        LOGGER.error("Failed to issue lock passcode for reservation {} on lock {} after {} attempts - will retry in background",
                reservation.id(), lockId, MAX_ATTEMPTS);
        return null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void revokePasscodes(int reservationId) {
        lockPasscodeRepository.findByReservationIdAndStatus(reservationId, LockPasscode.Status.ACTIVE)
                              .forEach(lockPasscode -> {
                                  if (lockPasscode.keyboardPwdId() == null) {
                                      LOGGER.error("ACTIVE lock passcode {} for reservation {} has no keyboardPwdId - data integrity issue, marking revoked without a Sifely call",
                                              lockPasscode.id(), reservationId);
                                  } else {
                                      try {
                                          providerFactory.getLockProvider().revokeCode(
                                                  String.valueOf(lockPasscode.lockId()), String.valueOf(lockPasscode.keyboardPwdId()));
                                      } catch (Exception e) {
                                          LOGGER.error("Failed to revoke Sifely passcode {} for reservation {} - marking revoked anyway",
                                                  lockPasscode.keyboardPwdId(), reservationId, e);
                                      }
                                  }

                                  lockPasscodeRepository.save(lockPasscode.withStatus(LockPasscode.Status.REVOKED)
                                                                          .withResolvedAt(LocalDateTime.now()));
                              });

        lockPasscodeRepository.findByReservationIdAndStatus(reservationId, LockPasscode.Status.FAILED)
                              .forEach(lockPasscode -> {
                                  lockPasscodeRepository.save(lockPasscode.withStatus(LockPasscode.Status.EXPIRED)
                                                                          .withResolvedAt(LocalDateTime.now()));

                                  LOGGER.info("Marked FAILED lock passcode {} for reservation {} as EXPIRED at checkout - no longer eligible for background retry",
                                          lockPasscode.id(), reservationId);
                              });
    }

    public List<UnacknowledgedDoorAccessNotification> getUnacknowledgedNotifications() {
        return lockPasscodeRepository.findByStatusAndAcknowledgedFalse(LockPasscode.Status.ACTIVE).stream()
                                     .map(lp -> {
                                         int roomNumber = reservationRepository.findById(lp.reservationId())
                                                                               .flatMap(r -> roomRepository.findById(r.roomId()))
                                                                               .map(Room::roomNumber)
                                                                               .orElse(0);
                                         return new UnacknowledgedDoorAccessNotification(lp.id(), lp.reservationId(), roomNumber, lp.resolvedAt());
                                     })
                                     .toList();
    }

    private String generatePasscode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = String.valueOf(1000 + RANDOM.nextInt(9000));

            if (!PasscodeRules.isConsecutiveOrRepeated(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a valid passcode after 20 attempts");
    }

    public record PasscodeResult(Outcome outcome, String passcode) {
        public enum Outcome {ISSUED, NO_LOCK_ASSIGNED, FAILED}
    }
}
