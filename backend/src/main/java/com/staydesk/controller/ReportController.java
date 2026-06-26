package com.staydesk.controller;

import com.staydesk.model.reporting.ReportSummaryResponse;
import com.staydesk.model.reporting.RoomReportRow;
import com.staydesk.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ReportSummaryResponse getSummary(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        LOGGER.info("Received request for report summary {} - {}", startDate, endDate);
        return reportService.getReportSummary(startDate, endDate);
    }

    @GetMapping("/by-room")
    public List<RoomReportRow> getByRoom(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        LOGGER.info("Received request for by-room report {} - {}", startDate, endDate);
        return reportService.getByRoom(startDate, endDate);
    }
}
