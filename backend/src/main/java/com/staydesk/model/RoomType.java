package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("room_types")
public record RoomType(@Id int id, String name, int availableCount, int unavailableCount, boolean petFriendly,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
}
