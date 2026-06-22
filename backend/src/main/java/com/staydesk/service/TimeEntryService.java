package com.staydesk.service;

import com.staydesk.exception.AlreadyClockedInException;
import com.staydesk.exception.NotClockedInException;
import com.staydesk.model.TimeEntry;
import com.staydesk.model.request.ClockInRequest;
import com.staydesk.model.request.ClockOutRequest;
import com.staydesk.model.request.ManualTimeEntryRequest;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.TimeEntryRepository;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final EmployeeRepository employeeRepository;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    public TimeEntryService(TimeEntryRepository timeEntryRepository, EmployeeRepository employeeRepository,
                            JdbcAggregateTemplate jdbcAggregateTemplate) {
        this.timeEntryRepository = timeEntryRepository;
        this.employeeRepository = employeeRepository;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
    }

    public TimeEntry clockIn(UUID employeeId, Jwt jwt, ClockInRequest request) {
        enforceOwnership(jwt, employeeId);

        timeEntryRepository.getOpenEntry(employeeId).ifPresent(e -> {
            throw new AlreadyClockedInException();
        });

        LocalDateTime now = LocalDateTime.now();
        OffsetDateTime clockIn = OffsetDateTime.now();

        return jdbcAggregateTemplate.insert(new TimeEntry(
                null, employeeId, clockIn, null,
                clockIn.toLocalDate(), null, request.notes(), now, now
        ));
    }

    public TimeEntry clockOut(UUID employeeId, Jwt jwt, ClockOutRequest request) {
        enforceOwnership(jwt, employeeId);

        TimeEntry open = timeEntryRepository.getOpenEntry(employeeId)
                                            .orElseThrow(NotClockedInException::new);

        OffsetDateTime clockOut = OffsetDateTime.now();
        BigDecimal hours = computeHours(open.clockIn(), clockOut);
        LocalDateTime now = LocalDateTime.now();

        TimeEntry updated = new TimeEntry(
                open.id(), open.employeeId(), open.clockIn(), clockOut,
                open.date(), hours, request.notes() != null ? request.notes() : open.notes(),
                open.createdAt(), now
        );

        return timeEntryRepository.save(updated);
    }

    public List<TimeEntry> getEmployeeTimesheet(UUID employeeId, Jwt jwt, LocalDate start, LocalDate end) {
        enforceOwnership(jwt, employeeId);

        return timeEntryRepository.getByEmployeeAndDateRange(employeeId, start, end);
    }

    public TimeEntry createManualEntry(ManualTimeEntryRequest request) {
        employeeRepository.findById(request.employeeId())
                          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee id"));

        LocalDateTime now = LocalDateTime.now();
        return jdbcAggregateTemplate.insert(new TimeEntry(
                null, request.employeeId(), request.clockIn(), request.clockOut(),
                request.date(), request.hours(), request.notes(), now, now
        ));
    }

    public TimeEntry updateEntry(Long id, ManualTimeEntryRequest request) {
        TimeEntry existing = timeEntryRepository.findById(id)
                                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Time entry not found"));

        return timeEntryRepository.save(new TimeEntry(
                existing.id(), existing.employeeId(), request.clockIn(), request.clockOut(),
                request.date(), request.hours(), request.notes(),
                existing.createdAt(), LocalDateTime.now()
        ));
    }

    public void deleteEntry(Long id) {
        if (!timeEntryRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Time entry not found");
        }

        timeEntryRepository.deleteById(id);
    }

    public List<TimeEntry> getPayPeriodEntries(LocalDate start, LocalDate end) {
        return timeEntryRepository.getByDateRange(start, end);
    }

    private void enforceOwnership(Jwt jwt, UUID employeeId) {
        UUID callerId = UUID.fromString(jwt.getSubject());

        if (!callerId.equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another employee's time entries");
        }
    }

    private BigDecimal computeHours(OffsetDateTime clockIn, OffsetDateTime clockOut) {
        long minutes = Duration.between(clockIn, clockOut).toMinutes();

        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
