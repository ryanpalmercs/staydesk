package com.staydesk.service;

import com.staydesk.model.Guest;
import com.staydesk.model.Reservation;
import com.staydesk.model.dto.RoomAccessEvent;
import com.staydesk.model.dto.SifelyLockRecord;
import com.staydesk.repository.GuestRepository;
import com.staydesk.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class RoomAccessLogService {
    private static final ZoneId PROPERTY_ZONE = ZoneId.of("America/Chicago");

    private final SifelyLockService sifelyLockService;
    private final ReservationRepository reservationRepository;
    private final GuestRepository guestRepository;

    public RoomAccessLogService(SifelyLockService sifelyLockService, ReservationRepository reservationRepository,
                                GuestRepository guestRepository) {
        this.sifelyLockService = sifelyLockService;
        this.reservationRepository = reservationRepository;
        this.guestRepository = guestRepository;
    }

    public List<RoomAccessEvent> getAccessLog(long lockId, int days) {
        int clampedDays = Math.clamp(days, 1, 90);
        long end = System.currentTimeMillis();
        long start = end - Duration.ofDays(clampedDays).toMillis();

        return sifelyLockService.getLockRecords(lockId, start, end).stream()
                                .filter(record -> record.recordType() != 47)
                                .map(this::resolve)
                                .sorted(Comparator.comparing(RoomAccessEvent::occurredAt).reversed())
                                .toList();
    }

    private RoomAccessEvent resolve(SifelyLockRecord record) {
        LocalDateTime occurredAt = Instant.ofEpochMilli(record.lockDate()).atZone(PROPERTY_ZONE).toLocalDateTime();
        String eventType = eventTypeLabel(record.recordType());
        boolean success = record.success() == 1;

        if (!success) {
            return new RoomAccessEvent(occurredAt, eventType, false, "Failed attempt", RoomAccessEvent.ActorType.UNAUTHORIZED);
        }

        String username = record.username();

        Optional<Integer> reservationId = PasscodeLabels.parseReservationId(username);
        if (reservationId.isPresent()) {
            Optional<String> guestName = reservationRepository.findById(reservationId.get())
                                                              .map(Reservation::guestId)
                                                              .flatMap(guestRepository::findById)
                                                              .map(Guest::name);

            if (guestName.isPresent()) {
                return new RoomAccessEvent(occurredAt, eventType, true, guestName.get(), RoomAccessEvent.ActorType.GUEST);
            }
        }

        Optional<String> staffUsername = PasscodeLabels.parseStaffUsername(username);
        if (staffUsername.isPresent()) {
            return new RoomAccessEvent(occurredAt, eventType, true, staffUsername.get(), RoomAccessEvent.ActorType.STAFF);
        }

        String label = (username == null || username.isBlank()) ? "Unrecognized passcode" : username;
        return new RoomAccessEvent(occurredAt, eventType, true, label, RoomAccessEvent.ActorType.UNKNOWN);
    }

    private String eventTypeLabel(int recordType) {
        return recordType == 4 ? "Passcode" : "Event type " + recordType;
    }
}