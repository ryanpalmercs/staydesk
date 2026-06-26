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

    public ReportSummaryResponse getReportSummary(LocalDate startDate, LocalDate endDate) {
        BigDecimal totalRevenue = reportRepository.getTotalRevenue(startDate, endDate);
        BigDecimal totalTax = reportRepository.getTotalTax(startDate, endDate);

        int occupiedNights = reportRepository.getOccupiedNightCount(startDate, endDate);
        int totalRooms = reportRepository.getTotalRoomCount();
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        int totalRoomNights = (int) (totalRooms * days);

        BigDecimal occupancyRate = totalRoomNights > 0 ?
                                   BigDecimal.valueOf(occupiedNights).divide(BigDecimal.valueOf(totalRoomNights), 4, RoundingMode.HALF_UP) :
                                   BigDecimal.ZERO;

        BigDecimal averageNightlyRate = occupiedNights > 0 ?
                                        totalRevenue.divide(BigDecimal.valueOf(occupiedNights), 2, RoundingMode.HALF_UP) :
                                        BigDecimal.ZERO;

        List<GuestCountRow> guestCountBreakdown = reportRepository.getGuestCountBreakdown(startDate, endDate);

        PeriodComparison periodComparison = buildComparison(startDate, days, totalRooms);

        return new ReportSummaryResponse(startDate, endDate, totalRevenue, totalTax, occupancyRate, occupiedNights,
                totalRoomNights, averageNightlyRate, guestCountBreakdown, periodComparison);
    }

    public List<RoomReportRow> getByRoom(LocalDate startDate, LocalDate endDate) {
        return reportRepository.getByRoom(startDate, endDate);
    }

    private PeriodComparison buildComparison(LocalDate startDate, long days, int totalRooms) {
        LocalDate previousStart = startDate.minusDays(days);

        BigDecimal previousRevenue = reportRepository.getTotalRevenue(previousStart, startDate);
        int previousOccupied =  reportRepository.getOccupiedNightCount(previousStart, startDate);
        int previousTotalRoomNights = (int) (totalRooms * days);

        BigDecimal previousOccupancyRate = previousTotalRoomNights > 0 ?
                                           BigDecimal.valueOf(previousOccupied).divide(BigDecimal.valueOf(previousTotalRoomNights), 4, RoundingMode.HALF_UP) :
                                           BigDecimal.ZERO;

        return new PeriodComparison(previousStart, startDate, previousRevenue, previousOccupancyRate);
    }
}
