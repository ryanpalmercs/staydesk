package com.staydesk.model.dto;

import java.time.LocalDate;

public record RoomTypeAvailabilityDto(int roomTypeId, LocalDate date, int availableCount) {
}
