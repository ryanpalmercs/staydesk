package com.staydesk.controller;

import com.staydesk.model.Account;
import com.staydesk.model.request.CreateSystemAdminRequest;
import com.staydesk.service.SystemAdminService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/admin")
public class SystemAdminController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemAdminController.class);

    private final SystemAdminService systemAdminService;

    public SystemAdminController(SystemAdminService systemAdminService) {
        this.systemAdminService = systemAdminService;
    }

    @PostMapping("system-admins")
    public ResponseEntity<Account> createSystemAdmin(@Valid @RequestBody CreateSystemAdminRequest request) {
        LOGGER.info("Creating system admin with email: {}", request.email());

        Account saved = systemAdminService.createSystemAdmin(request);
        URI location = URI.create("/admin/system-admins/" + saved.id());
        return ResponseEntity.created(location).body(saved);
    }
}
