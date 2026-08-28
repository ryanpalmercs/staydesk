package com.staydesk.service;

import com.staydesk.exception.EmployeeAlreadyExistsException;
import com.staydesk.model.Account;
import com.staydesk.model.EncryptedString;
import com.staydesk.model.Employee;
import com.staydesk.model.EmployeeType;
import com.staydesk.model.request.CreateEmployeeRequest;
import com.staydesk.model.request.UpdatePersonalInfoRequest;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.repository.EmployeeTypeRepository;
import com.staydesk.security.PiiCipher;
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

    private final EmployeeRepository employeeRepository;
    private final EmployeeTypeRepository employeeTypeRepository;
    private final SupabaseAdminClient supabaseAdminClient;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;
    private final StaffDoorAccessService staffDoorAccessService;
    private final PiiCipher piiCipher;
    private final QuickBooksEmployeeSyncService quickBooksEmployeeSyncService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           EmployeeTypeRepository employeeTypeRepository,
                           SupabaseAdminClient supabaseAdminClient, JdbcAggregateTemplate jdbcAggregateTemplate,
                           StaffDoorAccessService staffDoorAccessService, PiiCipher piiCipher,
                           QuickBooksEmployeeSyncService quickBooksEmployeeSyncService) {
        this.employeeRepository = employeeRepository;
        this.employeeTypeRepository = employeeTypeRepository;
        this.supabaseAdminClient = supabaseAdminClient;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
        this.staffDoorAccessService = staffDoorAccessService;
        this.piiCipher = piiCipher;
        this.quickBooksEmployeeSyncService = quickBooksEmployeeSyncService;
    }

    public Employee createEmployee(CreateEmployeeRequest createEmployeeRequest) {
        if (employeeRepository.findByUsername(createEmployeeRequest.username()).isPresent()) {
            LOGGER.warn("Employee with username {} already exists", createEmployeeRequest.username());
            throw new EmployeeAlreadyExistsException();
        }

        String emailHash = piiCipher.hash(createEmployeeRequest.email().strip().toLowerCase());
        if (employeeRepository.findByEmailHash(emailHash).isPresent()) {
            LOGGER.warn("Employee with this email already exists");
            throw new EmployeeAlreadyExistsException();
        }

        EmployeeType type = employeeTypeRepository.findById(createEmployeeRequest.employeeTypeId())
                                                  .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee type"));

        if (createEmployeeRequest.grantDoorAccess() && !PasscodeRules.isValidPin(createEmployeeRequest.pin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PIN must be 6 digits with no repeated or sequential digits");
        }

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

        jdbcAggregateTemplate.insert(new Account(supabaseId, Account.AccountKind.EMPLOYEE, null, true, now, now));

        Employee saved = jdbcAggregateTemplate.insert(new Employee(supabaseId, new EncryptedString(createEmployeeRequest.firstName()),
                new EncryptedString(createEmployeeRequest.lastName()), new EncryptedString(createEmployeeRequest.email()), emailHash,
                createEmployeeRequest.username(), createEmployeeRequest.employeeTypeId(), createEmployeeRequest.payRate(),
                createEmployeeRequest.hireDate(), true, createEmployeeRequest.contactInfo(), createEmployeeRequest.payRateType(),
                createEmployeeRequest.grantDoorAccess(), now, now, null));

        if (createEmployeeRequest.grantDoorAccess()) {
            staffDoorAccessService.grantAccess(saved, createEmployeeRequest.pin());
        }

        quickBooksEmployeeSyncService.syncOne(saved);

        return saved;
    }

    public void updateEmployeeRole(UUID id, int employeeTypeId) {
        Employee employee = employeeRepository.findById(id)
                                              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee id"));

        EmployeeType type = employeeTypeRepository.findById(employeeTypeId)
                                                  .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee type id"));

        supabaseAdminClient.updateUserRole(employee.id(), type.authRole().name());
        employeeRepository.updateEmployeeType(employee.id(), employeeTypeId);
    }

    public void updateEmployeePin(UUID id, String pin, boolean grantDoorAccess) {
        if (grantDoorAccess && !PasscodeRules.isValidPin(pin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PIN must be 6 digits with no repeated or sequential digits");
        }

        Employee employee = employeeRepository.findById(id)
                                              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee id"));

        supabaseAdminClient.resetPin(id, pin);
        staffDoorAccessService.revokeAccess(employee);
        employeeRepository.updateDoorAccessEnabled(id, grantDoorAccess);

        if (grantDoorAccess) {
            staffDoorAccessService.grantAccess(employee, pin);
        }
    }

    public void deactivateEmployee(UUID id) {
        Employee employee = employeeRepository.findById(id)
                                              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee id"));

        staffDoorAccessService.revokeAccess(employee);
        employeeRepository.updateDoorAccessEnabled(id, false);
        employeeRepository.deactivate(id);

        quickBooksEmployeeSyncService.syncActiveStatus(employee.quickbooksEmployeeId(), false);
    }

    public void updateEmployeePersonalInfo(UUID id, UpdatePersonalInfoRequest request) {
        Employee existing = employeeRepository.findById(id)
                                              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee id"));

        Employee updated = new Employee(existing.id(), new EncryptedString(request.firstName()), new EncryptedString(request.lastName()),
                existing.email(), existing.emailHash(), existing.username(), existing.employeeTypeId(), request.payRate(),
                request.hireDate(), existing.active(), request.contactInfo(), request.payRateType(),
                existing.doorAccessEnabled(), existing.createdAt(), LocalDateTime.now(), existing.quickbooksEmployeeId());

        employeeRepository.save(updated);
    }
}