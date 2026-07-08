package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

@Table("lock_passcodes")
public record LockPasscode(@Id int id, int reservationId, long lockId, @Nullable Long keyboardPwdId, String passcode,
                           LocalDateTime startDate, LocalDateTime endDate, Status status, boolean acknowledged,
                           LocalDateTime createdAt, @Nullable LocalDateTime resolvedAt) {

    public enum Status {
        ACTIVE, REVOKED, FAILED, EXPIRED
    }

    public LockPasscode withStatus(Status newStatus) {
        return new LockPasscode(id, reservationId, lockId, keyboardPwdId, passcode, startDate, endDate, newStatus,
                acknowledged, createdAt, resolvedAt);
    }

    public LockPasscode withKeyboardPwdId(Long newKeyboardPwdId) {
        return new LockPasscode(id, reservationId, lockId, newKeyboardPwdId, passcode, startDate, endDate, status,
                acknowledged, createdAt, resolvedAt);
    }

    public LockPasscode withAcknowledged(boolean newAcknowledged) {
        return new LockPasscode(id, reservationId, lockId, keyboardPwdId, passcode, startDate, endDate, status,
                newAcknowledged, createdAt, resolvedAt);
    }

    public LockPasscode withResolvedAt(LocalDateTime newResolvedAt) {
        return new LockPasscode(id, reservationId, lockId, keyboardPwdId, passcode, startDate, endDate, status,
                acknowledged, createdAt, newResolvedAt);
    }
}