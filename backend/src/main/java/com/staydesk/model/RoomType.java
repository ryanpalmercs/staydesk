package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("room_types")
public record RoomType(@Id int id, Name name, int availableCount, int unavailableCount,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum Name {
        TYPE_1, TYPE_2
    }
}
