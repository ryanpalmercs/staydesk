package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

@Table("rooms")
public record Room(@Id int id, int roomNumber, int roomTypeId, RoomStatus status, @Nullable Long sifelyLockId,
                   @Nullable String maintenanceNote, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum RoomStatus {
        AVAILABLE, OCCUPIED, MAINTENANCE
    }
}
