package com.staydesk.model.request;

import com.staydesk.model.Rate;
import com.staydesk.service.FolioService;

import java.time.LocalDate;
import java.util.List;

public record ReservationEstimateRequest(Rate.RateType rateType, int guestCount, LocalDate checkInDate, LocalDate checkOutDate,
                                         Integer guestId, List<FolioService.ExtraSelection> extras) {
}
