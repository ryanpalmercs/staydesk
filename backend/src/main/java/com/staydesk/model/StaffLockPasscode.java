package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("staff_lock_passcodes")
public record StaffLockPasscode(@Id int id, UUID employeeId, long lockId, long keyboardPwdId, Status status,
                                LocalDateTime createdAt, @Nullable LocalDateTime revokedAt) {
    public enum Status {ACTIVE, REVOKED}
}