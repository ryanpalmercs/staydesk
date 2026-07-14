package com.staydesk.model.request;

import com.staydesk.model.Rate;
import com.staydesk.model.Reservation;

import java.time.LocalDate;

public record CreateReservationRequest(int guestId, int roomTypeId, LocalDate checkInDate, LocalDate checkOutDate,
                                       Rate.RateType rateType, int guestCount, String roomPaymentMethodId, Reservation.Channel channel) {
}
