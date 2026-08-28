package com.staydesk.service;

import com.staydesk.exception.PayrollSyncException;
import com.staydesk.model.Employee;
import com.staydesk.model.PayrollSyncResponse;
import com.staydesk.model.TimesheetReport;
import com.staydesk.payroll.quickbooks.QuickBooksClient;
import com.staydesk.payroll.quickbooks.dto.QuickBooksTimeActivity;
import com.staydesk.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PayrollSyncService {

    private final Logger LOGGER = LoggerFactory.getLogger(PayrollSyncService.class);

    private final TimesheetService timesheetService;
    private final EmployeeRepository employeeRepository;
    private final QuickBooksClient quickBooksClient;

    public PayrollSyncService(TimesheetService timesheetService, EmployeeRepository employeeRepository,
                              QuickBooksClient quickBooksClient) {
        this.timesheetService = timesheetService;
        this.employeeRepository = employeeRepository;
        this.quickBooksClient = quickBooksClient;
    }

    public PayrollSyncResponse sync(LocalDate startDate, LocalDate endDate) {
        LOGGER.info("Starting QuickBooks payroll sync for {} - {}", startDate, endDate);

        TimesheetReport report = timesheetService.buildReport(startDate, endDate);

        Map<UUID, Employee> employeesById = employeeRepository.findAll()
                                                               .stream()
                                                               .collect(Collectors.toMap(Employee::id, e -> e));

        List<String> failedEmployees = new ArrayList<>();
        int employeesSynced = 0;

        for (TimesheetReport.EmployeeTimesheetRow row : report.employees()) {
            Employee employee = employeesById.get(row.employeeId());

            if (employee == null || employee.quickbooksEmployeeId() == null) {
                failedEmployees.add(row.name() + " (no QuickBooks employee mapping)");
                continue;
            }

            try {
                QuickBooksTimeActivity activity = QuickBooksTimeActivity.forPeriod(employee.quickbooksEmployeeId(), endDate,
                        row.totalHours(), "Pay period " + startDate + " to " + endDate);
                quickBooksClient.pushTimeActivity(activity);
                employeesSynced++;
            } catch (PayrollSyncException e) {
                LOGGER.warn("QuickBooks sync failed for employee {}: {}", row.name(), e.getMessage());
                failedEmployees.add(row.name() + " (" + e.getMessage() + ")");
            }
        }

        return new PayrollSyncResponse(startDate, endDate, employeesSynced, report.totalHours(), failedEmployees);
    }
}
