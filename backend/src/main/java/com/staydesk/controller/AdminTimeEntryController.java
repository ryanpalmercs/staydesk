package com.staydesk.controller;

import com.staydesk.model.TimeEntry;
import com.staydesk.model.request.ManualTimeEntryRequest;
import com.staydesk.model.request.UpdateTimeEntryRequest;
import com.staydesk.service.TimeEntryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/time-entries")
public class AdminTimeEntryController {
    private final Logger LOGGER = LoggerFactory.getLogger(AdminTimeEntryController.class);

    private final TimeEntryService timeEntryService;

    public AdminTimeEntryController(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    @GetMapping
    public List<TimeEntry> getPayPeriod(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        LOGGER.info("Received request to get timesheet for pay period {} - {}", start, end);

        return timeEntryService.getPayPeriodEntries(start, end);
    }

    @PostMapping
    public ResponseEntity<TimeEntry> createManualEntry(@Valid @RequestBody ManualTimeEntryRequest request) {
        LOGGER.info("Received request to create manual time entry for pay period {}", request);

        return ResponseEntity.status(HttpStatus.CREATED).body(timeEntryService.createManualEntry(request));
    }

    @PutMapping("{id}")
    public ResponseEntity<TimeEntry> updateEntry(@PathVariable long id, @Valid @RequestBody UpdateTimeEntryRequest request) {
        LOGGER.info("Received request to update time entry {}", id);

        return ResponseEntity.ok(timeEntryService.updateEntry(id, request));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<TimeEntry> deleteEntry(@PathVariable long id) {
        LOGGER.info("Received request to delete time entry {}", id);

        timeEntryService.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }
}
