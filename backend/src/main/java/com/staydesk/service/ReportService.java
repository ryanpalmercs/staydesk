package com.staydesk.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.staydesk.model.reporting.GuestCountRow;
import com.staydesk.model.reporting.PeriodComparison;
import com.staydesk.model.reporting.ReportSummaryResponse;
import com.staydesk.model.reporting.RoomReportRow;
import com.staydesk.repository.ReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class ReportService {
    private final ReportRepository reportRepository;
    private final TemplateEngine templateEngine;

    public ReportService(ReportRepository reportRepository, TemplateEngine templateEngine) {
        this.reportRepository = reportRepository;
        this.templateEngine = templateEngine;
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

    public byte[] generateCsv(ReportSummaryResponse report) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

        StringBuilder sb = new StringBuilder()
                .append(String.format("Report: %s – %s\n\n", report.startDate(), report.endDate()))
                .append("Summary\n")
                .append(String.format("Total Revenue,%s\n", currency.format(report.totalRevenue())))
                .append(String.format("Total Tax,%s\n", currency.format(report.totalTax())))
                .append(String.format("Occupancy Rate,%.2f%%\n", report.occupancyRate().multiply(BigDecimal.valueOf(100))))
                .append(String.format("Occupied Nights,%d\n", report.occupiedNightCount()))
                .append(String.format("Total Room Nights,%d\n", report.totalRoomNightCount()))
                .append(String.format("Avg Nightly Rate,%s\n\n", currency.format(report.averageNightlyRate())))
                .append("Room Breakdown\n")
                .append("Room,Booked Nights,Total Nights,Occupancy %,Revenue\n");

        report.roomBreakDown().forEach(r ->
                sb.append(String.format("%d,%d,%d,%.2f%%,%s\n",
                        r.roomNumber(), r.bookedNights(), r.totalNights(),
                        r.occupancyRate().multiply(BigDecimal.valueOf(100)),
                        currency.format(r.revenue())))
        );

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generatePdf(ReportSummaryResponse report) {
        Context context = new Context();
        context.setVariable("report", report);
        String html = templateEngine.process("report", context);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF generation failed");
        }
    }
}
