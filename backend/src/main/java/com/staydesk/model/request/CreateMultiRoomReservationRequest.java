package com.staydesk.model.request;

import com.staydesk.model.Rate;
import com.staydesk.model.Reservation;

import java.time.LocalDate;
import java.util.List;

public record CreateMultiRoomReservationRequest(int guestId, List<RoomLine> rooms, LocalDate checkInDate,
                                                LocalDate checkOutDate, Rate.RateType rateType, int guestCount,
                                                String roomPaymentMethodId, Reservation.Channel channel) {

    public record RoomLine(int roomTypeId, int quantity) {
    }
}
