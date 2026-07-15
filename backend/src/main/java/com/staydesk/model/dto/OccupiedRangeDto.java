package com.staydesk.model.dto;

import java.time.LocalDate;

public record OccupiedRangeDto(LocalDate checkIn, LocalDate checkOut) {
}