package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

@Table("lock_passcodes")
public record LockPasscode(@Id int id, int reservationId, long lockId, long keyboardPwdId, String passcode,
                           LocalDateTime startDate, LocalDateTime endDate, Status status, LocalDateTime createdAt,
                           @Nullable LocalDateTime revokedAt) {

    public enum Status {
        ACTIVE, REVOKED
    }
}