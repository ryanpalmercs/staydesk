package com.staydesk.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.staydesk.model.Employee;
import com.staydesk.model.TimeEntry;
import com.staydesk.model.TimesheetReport;
import com.staydesk.model.TimesheetReport.EmployeeTimesheetRow;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.TimeEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TimesheetService {

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");

    private final TimeEntryRepository timeEntryRepository;
    private final EmployeeRepository employeeRepository;
    private final TemplateEngine templateEngine;

    public TimesheetService(TimeEntryRepository timeEntryRepository, EmployeeRepository employeeRepository,
                            TemplateEngine templateEngine) {
        this.timeEntryRepository = timeEntryRepository;
        this.employeeRepository = employeeRepository;
        this.templateEngine = templateEngine;
    }

    private TimesheetReport buildReport(LocalDate startDate, LocalDate endDate) {
        List<TimeEntry> entries = timeEntryRepository.getByDateRange(startDate, endDate);

        Map<UUID, Employee> employeeMap = employeeRepository.findAll()
                                                            .stream()
                                                            .collect(Collectors.toMap(Employee::id, e -> e));

        Map<UUID, List<TimeEntry>> groupedTimeEntries = entries.stream()
                                                               .collect(Collectors.groupingBy(TimeEntry::employeeId));

        List<EmployeeTimesheetRow> rows = groupedTimeEntries.entrySet()
                                                            .stream()
                                                            .filter(entry -> employeeMap.containsKey(entry.getKey()))
                                                            .sorted(Comparator.comparing(entry -> employeeMap.get(entry.getKey()).lastName()))
                                                            .map(entry -> {
                                                                Employee employee = employeeMap.get(entry.getKey());

                                                                List<TimeEntry> sortedEntries = entry.getValue()
                                                                                                     .stream()
                                                                                                     .sorted(Comparator.comparing(TimeEntry::date)
                                                                                                                       .thenComparing(t ->
                                                                                                                               t.clockIn() != null ?
                                                                                                                               t.clockIn() :
                                                                                                                               LocalDateTime.MIN))
                                                                                                     .toList();

                                                                BigDecimal totalHours = sortedEntries.stream()
                                                                                                     .map(t ->
                                                                                                             t.hours() != null ?
                                                                                                             t.hours() :
                                                                                                             BigDecimal.ZERO)
                                                                                                     .reduce(BigDecimal.ZERO, BigDecimal::add);

                                                                return new EmployeeTimesheetRow(employee.name(),
                                                                        String.format("$%.2f/%s", employee.payRate(), employee.payRateTypeDisplayName()),
                                                                        sortedEntries,
                                                                        totalHours
                                                                );
                                                            }).toList();

        BigDecimal grandTotal = rows.stream()
                                    .map(EmployeeTimesheetRow::totalHours)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TimesheetReport(startDate, endDate, rows, grandTotal);
    }

    public byte[] generatePdf(LocalDate startDate, LocalDate endDate) {
        Context context = new Context();
        context.setVariable("report", buildReport(startDate, endDate));
        String html = templateEngine.process("timesheet", context);

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

    public byte[] generateCsv(LocalDate startDate, LocalDate endDate) {
        TimesheetReport report = buildReport(startDate, endDate);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Employee,Date,Day,Clock In,Clock Out,Hours,Notes\n");

        report.employees().forEach(row -> {
            row.entries()
               .stream()
               .map(e -> String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                       row.name(),
                       e.date(),
                       e.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US),
                       e.clockIn() != null ? formatter.format(e.clockIn().toLocalTime()) : "",
                       e.clockOut() != null ? formatter.format(e.clockOut().toLocalTime()) : "",
                       e.hours() != null ? e.hours().toPlainString() : "",
                       e.notes() != null ? e.notes().replace("\"", "\"\"") : ""
               )).forEach(stringBuilder::append);
            stringBuilder.append(String.format("\"%s Total\",,,,,\"%.2f\",\n\n", row.name(), row.totalHours()));
        });

        stringBuilder.append(String.format("\"Grand Total\",,,,,\"%.2f\",\n", report.totalHours()));

        return stringBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
