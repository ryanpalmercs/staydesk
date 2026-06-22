package com.staydesk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.exception.EmployeeAlreadyExistsException;
import com.staydesk.model.Employee;
import com.staydesk.model.EmployeeType;
import com.staydesk.model.request.CreateEmployeeRequest;
import com.staydesk.model.request.UpdatePersonalInfoRequest;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.EmployeeTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmployeeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmployeeService.class);

    private final ObjectMapper objectMapper;
    private final EmployeeRepository employeeRepository;
    private final EmployeeTypeRepository employeeTypeRepository;
    private final SupabaseAdminClient supabaseAdminClient;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    public EmployeeService(ObjectMapper objectMapper, EmployeeRepository employeeRepository, EmployeeTypeRepository employeeTypeRepository,
                           SupabaseAdminClient supabaseAdminClient, JdbcAggregateTemplate jdbcAggregateTemplate) {
        this.objectMapper = objectMapper;
        this.employeeRepository = employeeRepository;
        this.employeeTypeRepository = employeeTypeRepository;
        this.supabaseAdminClient = supabaseAdminClient;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
    }

    public Employee createEmployee(CreateEmployeeRequest createEmployeeRequest) {
        if (employeeRepository.findByUsername(createEmployeeRequest.username()).isPresent()) {
            LOGGER.warn("Employee with username {} already exists", createEmployeeRequest.username());
            throw new EmployeeAlreadyExistsException();
        }

        if (employeeRepository.findByEmail(createEmployeeRequest.email()).isPresent()) {
            LOGGER.warn("Employee with email {} already exists", createEmployeeRequest.email());
            throw new EmployeeAlreadyExistsException();
        }

        EmployeeType type = employeeTypeRepository.findById(createEmployeeRequest.employeeTypeId())
                                                  .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee type"));

        UUID supabaseId;

        try {
            supabaseId = supabaseAdminClient.createUser(
                    createEmployeeRequest.email(),
                    createEmployeeRequest.pin(),
                    type.authRole().name()
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new EmployeeAlreadyExistsException();
            }

            throw e;
        }

        LocalDateTime now = LocalDateTime.now();

        return jdbcAggregateTemplate.insert(new Employee(supabaseId, createEmployeeRequest.firstName(),
                createEmployeeRequest.lastName(), createEmployeeRequest.email(), createEmployeeRequest.username(),
                createEmployeeRequest.employeeTypeId(), createEmployeeRequest.payRate(), createEmployeeRequest.hireDate(),
                true, createEmployeeRequest.contactInfo(), createEmployeeRequest.payRateType(), now, now));
    }

    public void updateEmployeeRole(UUID id, int employeeTypeId) {
        Employee employee = employeeRepository.findById(id)
                                              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee id"));

        EmployeeType type = employeeTypeRepository.findById(employeeTypeId)
                                                  .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee type id"));

        supabaseAdminClient.updateUserRole(employee.id(), type.authRole().name());
        employeeRepository.updateEmployeeType(employee.id(), employeeTypeId);
    }

    public void updateEmployeePin(UUID id, String pin) {
        supabaseAdminClient.resetPin(id, pin);
    }

    public void updateEmployeePersonalInfo(UUID id, UpdatePersonalInfoRequest request) throws JsonProcessingException {
        employeeRepository.findById(id)
                          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee id"));

        String contactInfoString = objectMapper.writeValueAsString(request.contactInfo());

        employeeRepository.updatePersonalInfo(id, request.firstName(), request.lastName(), request.payRate(), request.hireDate(),
                contactInfoString, request.payRateType().name());
    }
}
