package com.staydesk.controller;

import com.staydesk.model.TimeEntry;
import com.staydesk.model.request.ClockInRequest;
import com.staydesk.model.request.ClockOutRequest;
import com.staydesk.service.TimeEntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/employees/{id}")
public class EmployeeTimeController {
    private final Logger LOGGER = LoggerFactory.getLogger(EmployeeTimeController.class);

    private final TimeEntryService timeEntryService;

    public EmployeeTimeController(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    @PostMapping("clock-in")
    public ResponseEntity<TimeEntry> clockIn(@PathVariable UUID id, @RequestBody ClockInRequest clockInRequest,
                                             @AuthenticationPrincipal Jwt jwt) {
        LOGGER.info("Received request to clock-in for employee {}", id);

        return ResponseEntity.status(HttpStatus.CREATED).body(timeEntryService.clockIn(id, jwt, clockInRequest));
    }

    @PostMapping("clock-out")
    public ResponseEntity<TimeEntry> clockOut(@PathVariable UUID id, @RequestBody ClockOutRequest clockOutRequest,
                                              @AuthenticationPrincipal Jwt jwt) {
        LOGGER.info("Received request to clock-out for employee {}", id);

        return ResponseEntity.ok(timeEntryService.clockOut(id, jwt, clockOutRequest));
    }

    @GetMapping("time-entries")
    public List<TimeEntry> getTimesheet(@PathVariable UUID id, @RequestParam LocalDate start,
                                        @RequestParam LocalDate end, @AuthenticationPrincipal Jwt jwt) {
        LOGGER.info("Received request to get timesheet for employee {}", id);

        return timeEntryService.getEmployeeTimesheet(id, jwt, start, end);
    }

    @GetMapping("clock-status")
    public ResponseEntity<TimeEntry> getClockStatus(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        LOGGER.info("Received request to get clock status for employee {}", id);

        return timeEntryService.getClockStatus(id, jwt)
                                .map(ResponseEntity::ok)
                                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
