package com.staydesk.model.dto;

import java.time.LocalDate;

public record RemoteCheckInSummary(String confirmationCode, String roomTypeName, LocalDate checkInDate,
                                   LocalDate checkOutDate, int guestCount, String guestFirstName) {
}
