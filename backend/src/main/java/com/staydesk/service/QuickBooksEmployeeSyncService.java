package com.staydesk.service;

import com.staydesk.model.Employee;
import com.staydesk.model.EmployeeType;
import com.staydesk.model.QuickBooksEmployeeSyncResponse;
import com.staydesk.payroll.quickbooks.QuickBooksClient;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.EmployeeTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class QuickBooksEmployeeSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuickBooksEmployeeSyncService.class);

    private final EmployeeRepository employeeRepository;
    private final EmployeeTypeRepository employeeTypeRepository;
    private final QuickBooksClient quickBooksClient;

    public QuickBooksEmployeeSyncService(EmployeeRepository employeeRepository,
                                         EmployeeTypeRepository employeeTypeRepository,
                                         QuickBooksClient quickBooksClient) {
        this.employeeRepository = employeeRepository;
        this.employeeTypeRepository = employeeTypeRepository;
        this.quickBooksClient = quickBooksClient;
    }

    public QuickBooksEmployeeSyncResponse syncAll() {
        List<String> created = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        employeeRepository.findAll().forEach(employee -> syncEmployee(employee, isPayrollEnabledSafely(), created, matched, failed));

        return new QuickBooksEmployeeSyncResponse(created, matched, failed);
    }

    public void syncOne(Employee employee) {
        syncEmployee(employee, isPayrollEnabledSafely(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private boolean isPayrollEnabledSafely() {
        try {
            boolean payrollEnabled = quickBooksClient.isPayrollEnabled();
            LOGGER.info("QuickBooks Payroll is {}", payrollEnabled ? "enabled" : "not enabled");
            return payrollEnabled;
        } catch (Exception e) {
            LOGGER.warn("Could not determine QuickBooks Payroll status, assuming enabled to avoid sending unsupported fields: {}",
                    e.getMessage());
            return true;
        }
    }

    private void syncEmployee(Employee employee, boolean payrollEnabled, List<String> created, List<String> matched,
                              List<String> failed) {
        try {
            String jobTitle = employeeTypeRepository.findById(employee.employeeTypeId())
                                                    .map(EmployeeType::name)
                                                    .orElse(null);

            if (employee.quickbooksEmployeeId() != null) {
                quickBooksClient.updateEmployee(employee, employee.quickbooksEmployeeId(), jobTitle, payrollEnabled);
                matched.add(employee.name());
                return;
            }

            Optional<String> existingId = quickBooksClient.findEmployeeByDisplayName(employee.name());

            String quickBooksEmployeeId;
            if (existingId.isPresent()) {
                quickBooksEmployeeId = existingId.get();
                matched.add(employee.name());
                quickBooksClient.updateEmployee(employee, quickBooksEmployeeId, jobTitle, payrollEnabled);
            } else {
                quickBooksEmployeeId = quickBooksClient.createEmployee(employee, jobTitle, payrollEnabled);
                created.add(employee.name());
            }

            employeeRepository.updateQuickbooksEmployeeId(employee.id(), quickBooksEmployeeId);
        } catch (Exception e) {
            LOGGER.warn("QuickBooks employee sync failed for {}: {}", employee.name(), e.getMessage());
            failed.add(employee.name() + " (" + e.getMessage() + ")");
        }
    }

    public void syncActiveStatus(String quickbooksEmployeeId, boolean active) {
        if (quickbooksEmployeeId == null || quickbooksEmployeeId.isBlank()) {
            return;
        }

        try {
            quickBooksClient.setEmployeeActive(quickbooksEmployeeId, active);
        } catch (Exception e) {
            LOGGER.warn("QuickBooks active-status sync failed for QuickBooks employee {}: {}", quickbooksEmployeeId, e.getMessage());
        }
    }
}
