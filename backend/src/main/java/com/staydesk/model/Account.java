package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("accounts")
public record Account(@Id UUID id, AccountKind kind, String displayName, boolean active,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum AccountKind {
        EMPLOYEE, SYSTEM_ADMIN
    }
}
