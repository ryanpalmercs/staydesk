package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("reservations")
public record Reservation(@Id int id, int guestId, int roomId, LocalDate checkInDate, LocalDate checkOutDate, String status,
                          @Nullable LocalDateTime checkedInAt, @Nullable LocalDateTime checkedOutAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
