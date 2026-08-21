package com.staydesk.service;

import com.staydesk.model.Employee;
import com.staydesk.model.QuickBooksEmployeeSyncResponse;
import com.staydesk.payroll.quickbooks.QuickBooksClient;
import com.staydesk.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuickBooksEmployeeSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuickBooksEmployeeSyncService.class);

    private final EmployeeRepository employeeRepository;
    private final QuickBooksClient quickBooksClient;

    public QuickBooksEmployeeSyncService(EmployeeRepository employeeRepository, QuickBooksClient quickBooksClient) {
        this.employeeRepository = employeeRepository;
        this.quickBooksClient = quickBooksClient;
    }

    public QuickBooksEmployeeSyncResponse syncAll() {
        List<String> created = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        employeeRepository.findAll().stream()
                .filter(employee -> employee.quickbooksEmployeeId() == null)
                .forEach(employee -> syncEmployee(employee, created, matched, failed));

        return new QuickBooksEmployeeSyncResponse(created, matched, failed);
    }

    public void syncOne(Employee employee) {
        syncEmployee(employee, List.of(), List.of(), List.of());
    }

    private void syncEmployee(Employee employee, List<String> created, List<String> matched, List<String> failed) {
        try {
            String quickBooksEmployeeId = quickBooksClient.findEmployeeByDisplayName(employee.name())
                    .map(id -> {
                        matched.add(employee.name());
                        return id;
                    })
                    .orElseGet(() -> {
                        String newId = quickBooksClient.createEmployee(employee.firstName().value(), employee.lastName().value());
                        created.add(employee.name());
                        return newId;
                    });

            employeeRepository.updateQuickbooksEmployeeId(employee.id(), quickBooksEmployeeId);
        } catch (Exception e) {
            LOGGER.warn("QuickBlooks employee sync failed for {}: {}", employee.name(), e.getMessage());
            failed.add(employee.name() + " (" + e.getMessage() + ")");
        }
    }
}
