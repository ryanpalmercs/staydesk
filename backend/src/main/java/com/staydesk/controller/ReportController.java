package com.staydesk.controller;

import com.staydesk.model.reporting.ReportSummaryResponse;
import com.staydesk.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ReportSummaryResponse getSummary(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
                                            @RequestParam LocalDate comparisonStartDate,
                                            @RequestParam LocalDate comparisonEndDate) {
        LOGGER.info("Received request for report summary {} - {}", startDate, endDate);
        return reportService.getReportSummary(startDate, endDate, comparisonStartDate, comparisonEndDate);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody ReportSummaryResponse report, @RequestParam String format) {
        LOGGER.info("Received request to export report {} - {} as {}", report.startDate(), report.endDate(), format);

        return switch (format.toLowerCase()) {
            case "pdf" -> ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + report.startDate() + "-" + report.endDate() + ".pdf\"")
                                        .body(reportService.generatePdf(report));
            case "csv" -> ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + report.startDate() + "-" + report.endDate() + ".csv\"")
                                        .body(reportService.generateCsv(report));
            default ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid format: must be 'csv' or 'pdf'");
        };
    }
}
