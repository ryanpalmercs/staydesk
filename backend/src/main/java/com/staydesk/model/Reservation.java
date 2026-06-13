package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("reservations")
public record Reservation(@Id int id, int guestId, int roomId, LocalDate checkInDate, LocalDate checkOutDate, String status,
                          LocalDateTime checkedInAt, LocalDateTime checkedOutAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
