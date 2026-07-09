package com.staydesk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.lock.LockStatus;
import com.staydesk.model.dto.SifelyWebhookPayload;
import com.staydesk.model.dto.SifelyWebhookRecord;
import com.staydesk.repository.LockStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SifelyWebhookService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SifelyWebhookService.class);
    private static final ZoneId PROPERTY_ZONE = ZoneId.of("America/Chicago");

    // recordType catalog: events that tell us the bolt was successfully unlocked or locked.
    // Everything else (alarms, door-sensor/system status, special modes) doesn't tell us
    // current bolt position, so it only updates battery/lastSeen, not state.
    // 47 ("Locked") isn't in Sifely's documented webhook event catalog, but confirmed via
    // timestamp cross-reference against the Sifely app's own access log.
    private static final Set<Integer> UNLOCK_EVENTS = Set.of(1, 4, 7, 8, 9, 12, 49, 57, 67, 84, 92);
    private static final Set<Integer> LOCK_EVENTS = Set.of(11, 33, 34, 35, 47, 52, 61, 69, 86);

    private final LockStateRepository lockStateRepository;
    private final ObjectMapper objectMapper;

    public SifelyWebhookService(LockStateRepository lockStateRepository, ObjectMapper objectMapper) {
        this.lockStateRepository = lockStateRepository;
        this.objectMapper = objectMapper;
    }

    public void process(SifelyWebhookPayload payload) {
        List<SifelyWebhookRecord> records;

        try {
            records = objectMapper.readValue(payload.records(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SifelyWebhookRecord.class));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Sifely webhook records for lock {}: {}", payload.lockId(), e.getMessage());
            return;
        }

        records.forEach(this::applyRecord);
    }

    private void applyRecord(SifelyWebhookRecord record) {
        LocalDateTime eventAt = Instant.ofEpochMilli(record.lockDate()).atZone(PROPERTY_ZONE).toLocalDateTime();
        Optional<LockStatus.State> derivedState = deriveState(record);

        if (derivedState.isPresent()) {
            lockStateRepository.upsertState(record.lockId(), derivedState.get().name(), record.electricQuantity(), eventAt);
        } else {
            lockStateRepository.upsertBatteryOnly(record.lockId(), record.electricQuantity(), eventAt);
        }
    }

    private Optional<LockStatus.State> deriveState(SifelyWebhookRecord record) {
        if (record.success() != 1) {
            return Optional.empty();
        }
        if (UNLOCK_EVENTS.contains(record.recordType())) {
            return Optional.of(LockStatus.State.UNLOCKED);
        }
        if (LOCK_EVENTS.contains(record.recordType())) {
            return Optional.of(LockStatus.State.LOCKED);
        }
        return Optional.empty();
    }
}
