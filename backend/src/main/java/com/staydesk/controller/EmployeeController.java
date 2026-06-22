package com.staydesk.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.staydesk.exception.EmployeeAlreadyExistsException;
import com.staydesk.model.Employee;
import com.staydesk.model.EmployeeType;
import com.staydesk.model.request.CreateEmployeeRequest;
import com.staydesk.model.request.UpdateEmployeeRequest;
import com.staydesk.model.request.UpdatePersonalInfoRequest;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.EmployeeTypeRepository;
import com.staydesk.service.EmployeeService;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class EmployeeController {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final EmployeeTypeRepository employeeTypeRepository;

    public EmployeeController(EmployeeRepository employeeRepository, EmployeeService employeeService,
                              EmployeeTypeRepository employeeTypeRepository) {
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.employeeTypeRepository = employeeTypeRepository;
    }

    @GetMapping("employees")
    public List<Employee> getEmployees() {
        LOGGER.info("Getting employee list");

        return employeeRepository.findAll();
    }

    @GetMapping("employees/{id}")
    public ResponseEntity<Employee> getEmployee(@PathVariable UUID id) {
        LOGGER.info("Getting employee with id: {}", id);

        return employeeRepository.findById(id)
                                 .map(ResponseEntity::ok)
                                 .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("employees")
    public ResponseEntity<Employee> createEmployee(@Valid @RequestBody CreateEmployeeRequest createEmployeeRequest) {
        LOGGER.info("Creating employee with email: {}", createEmployeeRequest.email());

        try {
            Employee savedEmployee = employeeService.createEmployee(createEmployeeRequest);
            URI location = URI.create("/admin/employees/" + savedEmployee.id());
            return ResponseEntity.created(location).body(savedEmployee);
        } catch (EmployeeAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PutMapping("employees/{id}/role")
    public ResponseEntity<Void> updateEmployeeRole(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateEmployeeRequest request) {
        LOGGER.info("Updating employee role with id: {}", id);

        employeeService.updateEmployeeRole(id, request.employeeTypeId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("employees/{id}/pin")
    public ResponseEntity<Void> updateEmployeePin(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateEmployeeRequest request) {
        LOGGER.info("Updating employee pin with id: {}", id);

        employeeService.updateEmployeePin(id, request.pin());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("employees/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
        LOGGER.info("Deleting employee with id: {}", id);

        employeeRepository.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("employeeTypes")
    public List<EmployeeType> getEmployeeTypes() {
        LOGGER.info("Getting employee types");

        return employeeTypeRepository.findAll();
    }

    @PutMapping("employees/{id}")
    public ResponseEntity<Void> updateEmployeePersonalInfo(@PathVariable UUID id, @Valid @RequestBody UpdatePersonalInfoRequest request) {
        LOGGER.info("Updating employee personal info with id: {}", id);

        try {
            employeeService.updateEmployeePersonalInfo(id, request);
            return ResponseEntity.noContent().build();
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
