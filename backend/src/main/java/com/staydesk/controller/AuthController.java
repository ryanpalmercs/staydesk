package com.staydesk.controller;

import com.staydesk.model.Employee;
import com.staydesk.model.dto.SupabaseAuthResponse;
import com.staydesk.model.request.EmployeeLoginRequest;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.service.StaffDoorAccessService;
import com.staydesk.service.SupabaseAdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final EmployeeRepository employeeRepository;
    private final SupabaseAdminClient supabaseAdminClient;
    private final StaffDoorAccessService staffDoorAccessService;

    public AuthController(EmployeeRepository employeeRepository, SupabaseAdminClient supabaseAdminClient,
                          StaffDoorAccessService staffDoorAccessService) {
        this.employeeRepository = employeeRepository;
        this.supabaseAdminClient = supabaseAdminClient;
        this.staffDoorAccessService = staffDoorAccessService;
    }

    @PostMapping("employee/login")
    public ResponseEntity<SupabaseAuthResponse> login(@RequestBody EmployeeLoginRequest request) {
        LOGGER.info("Received login request for username: {}", request.username());
        Optional<Employee> employee = employeeRepository.findByUsername(request.username());

        if (employee.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            SupabaseAuthResponse authResponse = supabaseAdminClient.signIn(employee.get().email(), request.pin());
            staffDoorAccessService.syncAccessIfNeeded(employee.get(), request.pin());
            return ResponseEntity.ok(authResponse);
        } catch (Exception ex) {
            LOGGER.error("Supabase signIn failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}