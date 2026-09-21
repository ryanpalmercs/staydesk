package com.staydesk.model.request;

import com.staydesk.model.Rate;
import com.staydesk.model.Reservation;
import com.staydesk.service.FolioService;

import java.time.LocalDate;
import java.util.List;

public record CreateReservationRequest(int guestId, int roomTypeId, LocalDate checkInDate, LocalDate checkOutDate,
                                       Rate.RateType rateType, int guestCount, String roomPaymentMethodId, Reservation.Channel channel,
                                       List<FolioService.ExtraSelection> extras) {
}
