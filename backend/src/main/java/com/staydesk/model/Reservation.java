package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("reservations")
public record Reservation(@Id int id, @Nullable Integer guestId, @Nullable Integer roomId, int roomTypeId,
                          LocalDate checkInDate, LocalDate checkOutDate,
                          ReservationStatus status, @Nullable LocalDateTime checkedInAt,
                          @Nullable LocalDateTime checkedOutAt, Rate.RateType rateType, int guestCount,
                          Channel channel, boolean legalHold, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum ReservationStatus {
        CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW
    }

    public enum Channel {
        WALK_IN, PHONE
    }
}
