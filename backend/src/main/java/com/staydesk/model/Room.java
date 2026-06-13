package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("rooms")
public record Room(@Id int id, int roomNumber, RoomType type, BigDecimal nightlyRate, RoomStatus status,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum RoomType {
        TYPE_1, TYPE_2
    }

    public enum RoomStatus {
        AVAILABLE, OCCUPIED, MAINTENANCE, RESERVED
    }
}
