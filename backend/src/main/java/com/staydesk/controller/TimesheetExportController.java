package com.staydesk.controller;

import com.staydesk.service.TimesheetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/timesheets")
public class TimesheetExportController {
    private final Logger LOGGER = LoggerFactory.getLogger(TimesheetExportController.class);

    private final TimesheetService timesheetService;

    public TimesheetExportController(TimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportTimesheets(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
                                                   @RequestParam String format) {
        LOGGER.info("Received request to export timesheet {} - {} as {}", startDate, endDate, format);

        return switch (format.toLowerCase()) {
            case "pdf" -> ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timesheet-" + startDate + "-" + endDate + ".pdf\"")
                                        .body(timesheetService.generatePdf(startDate, endDate));
            case "csv" -> ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timesheet-" + startDate + "-" + endDate + ".csv\"")
                                        .body(timesheetService.generateCsv(startDate, endDate));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid format: must be 'csv' or 'pdf'");
        };
    }
}
