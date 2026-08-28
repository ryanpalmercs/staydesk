package com.staydesk.controller;

import com.staydesk.model.PayrollSyncResponse;
import com.staydesk.model.QuickBooksEmployeeSyncResponse;
import com.staydesk.model.request.PayrollSyncRequest;
import com.staydesk.service.PayrollSyncService;
import com.staydesk.service.QuickBooksEmployeeSyncService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/payroll")
public class PayrollSyncController {
    private final Logger LOGGER = LoggerFactory.getLogger(PayrollSyncController.class);

    private final PayrollSyncService payrollSyncService;
    private final QuickBooksEmployeeSyncService quickBooksEmployeeSyncService;

    public PayrollSyncController(PayrollSyncService payrollSyncService,
                                 QuickBooksEmployeeSyncService quickBooksEmployeeSyncService) {
        this.payrollSyncService = payrollSyncService;
        this.quickBooksEmployeeSyncService = quickBooksEmployeeSyncService;
    }

    @PostMapping("/sync")
    public PayrollSyncResponse sync(@Valid @RequestBody PayrollSyncRequest request) {
        LOGGER.info("Received request to sync payroll {} - {} to QuickBooks", request.startDate(), request.endDate());

        return payrollSyncService.sync(request.startDate(), request.endDate());
    }

    @PostMapping("/sync-employees")
    public QuickBooksEmployeeSyncResponse syncEmployees() {
        LOGGER.info("Received request to sync employees to QuickBooks");
        return quickBooksEmployeeSyncService.syncAll();
    }
}
