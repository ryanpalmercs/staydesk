package com.staydesk.service;

import com.staydesk.model.reporting.GuestCountRow;
import com.staydesk.model.reporting.PeriodComparison;
import com.staydesk.model.reporting.ReportSummaryResponse;
import com.staydesk.model.reporting.RoomReportRow;
import com.staydesk.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReportService {
    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public ReportSummaryResponse getReportSummary(LocalDate startDate, LocalDate endDate, LocalDate comparisonStartDate,
                                                  LocalDate comparisonEndDate) {
        BigDecimal totalRevenue = reportRepository.getTotalRevenue(startDate, endDate);
        BigDecimal totalTax = reportRepository.getTotalTax(startDate, endDate);

        int totalRooms = reportRepository.getTotalRoomCount();
        int totalNights = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        int totalRoomNights = totalRooms * totalNights;

        List<RoomReportRow> roomBreakDown = reportRepository.getByRoom(startDate, endDate)
                                                            .stream()
                                                            .map(r -> new RoomReportRow(
                                                                    r.roomId(),
                                                                    r.roomNumber(),
                                                                    r.bookedNights(),
                                                                    totalNights,
                                                                    totalNights > 0 ?
                                                                    BigDecimal.valueOf(r.bookedNights()).divide(BigDecimal.valueOf(totalNights), 4, RoundingMode.HALF_UP) :
                                                                    BigDecimal.ZERO,
                                                                    r.revenue()
                                                            ))
                                                            .toList();

        int occupiedNights = roomBreakDown.stream().mapToInt(RoomReportRow::bookedNights).sum();

        BigDecimal occupancyRate = totalRoomNights > 0 ?
                                   BigDecimal.valueOf(occupiedNights).divide(BigDecimal.valueOf(totalRoomNights), 4, RoundingMode.HALF_UP) :
                                   BigDecimal.ZERO;

        BigDecimal averageNightlyRate = occupiedNights > 0 ?
                                        totalRevenue.divide(BigDecimal.valueOf(occupiedNights), 2, RoundingMode.HALF_UP) :
                                        BigDecimal.ZERO;

        List<GuestCountRow> guestCountBreakdown = reportRepository.getGuestCountBreakdown(startDate, endDate);

        PeriodComparison periodComparison = buildComparison(comparisonStartDate, comparisonEndDate, totalRooms);

        return new ReportSummaryResponse(startDate, endDate, totalRevenue, totalTax, occupancyRate, occupiedNights,
                totalRoomNights, averageNightlyRate, guestCountBreakdown, periodComparison, roomBreakDown);
    }

    private PeriodComparison buildComparison(LocalDate startDate, LocalDate endDate, int totalRooms) {
        BigDecimal previousRevenue = reportRepository.getTotalRevenue(startDate, endDate);

        int previousNights = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        int previousTotalRoomNights = totalRooms * previousNights;

        int previousOccupied = reportRepository.getByRoom(startDate, endDate)
                                               .stream()
                                               .mapToInt(RoomReportRow::bookedNights)
                                               .sum();

        BigDecimal previousOccupancyRate = previousTotalRoomNights > 0 ?
                                           BigDecimal.valueOf(previousOccupied).divide(BigDecimal.valueOf(previousTotalRoomNights), 4, RoundingMode.HALF_UP) :
                                           BigDecimal.ZERO;

        return new PeriodComparison(startDate, endDate, previousRevenue, previousOccupancyRate);
    }
}
