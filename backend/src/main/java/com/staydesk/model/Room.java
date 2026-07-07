package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("rooms")
public record Room(@Id int id, int roomNumber, RoomType type, RoomStatus status, @Nullable Long sifelyLockId,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum RoomType {
        TYPE_1, TYPE_2
    }

    public enum RoomStatus {
        AVAILABLE, OCCUPIED, MAINTENANCE
    }
}
