package com.staydesk.controller;

import com.staydesk.model.Employee;
import com.staydesk.model.request.EmployeeLoginRequest;
import com.staydesk.repository.EmployeeRepository;
import com.staydesk.service.SupabaseAdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final EmployeeRepository employeeRepository;
    private final SupabaseAdminClient supabaseAdminClient;

    public AuthController(EmployeeRepository employeeRepository, SupabaseAdminClient supabaseAdminClient) {
        this.employeeRepository = employeeRepository;
        this.supabaseAdminClient = supabaseAdminClient;
    }

    @PostMapping("employee/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody EmployeeLoginRequest request) {
        LOGGER.info("Received login request: {}", request);
        Optional<Employee> employee = employeeRepository.findByUsername(request.username());

        if (employee.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            return ResponseEntity.ok(supabaseAdminClient.signIn(employee.get().email(), request.pin()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}