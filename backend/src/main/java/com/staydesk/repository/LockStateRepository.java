package com.staydesk.repository;

import com.staydesk.model.LockState;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDateTime;

public interface LockStateRepository extends CrudRepository<LockState, Long> {

    @Modifying
    @Query("""
            INSERT INTO lock_states (lock_id, state, battery_level, last_event_at, updated_at)
            VALUES (:lockId, :state, :batteryLevel, :lastEventAt, now())
            ON CONFLICT (lock_id) DO UPDATE SET
                state = EXCLUDED.state,
                battery_level = EXCLUDED.battery_level,
                last_event_at = EXCLUDED.last_event_at,
                updated_at = now()
            """)
    void upsertState(long lockId, String state, Integer batteryLevel, LocalDateTime lastEventAt);

    @Modifying
    @Query("""
            INSERT INTO lock_states (lock_id, state, battery_level, last_event_at, updated_at)
            VALUES (:lockId, 'UNKNOWN', :batteryLevel, :lastEventAt, now())
            ON CONFLICT (lock_id) DO UPDATE SET
                battery_level = EXCLUDED.battery_level,
                last_event_at = EXCLUDED.last_event_at,
                updated_at = now()
            """)
    void upsertBatteryOnly(long lockId, Integer batteryLevel, LocalDateTime lastEventAt);
}
