package com.staydesk.lock;

import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

public record LockStatus(State state, @Nullable Integer batteryLevel, @Nullable LocalDateTime lastSeen) {
    public enum State {LOCKED, UNLOCKED, UNKNOWN}
}
