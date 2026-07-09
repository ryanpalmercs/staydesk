package com.staydesk.model;

import com.staydesk.lock.LockStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

@Table("lock_states")
public record LockState(@Id long lockId, LockStatus.State state, @Nullable Integer batteryLevel,
                        @Nullable LocalDateTime lastEventAt, LocalDateTime updatedAt) {
}
