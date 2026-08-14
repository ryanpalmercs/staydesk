package com.staydesk.service;

import com.staydesk.controller.SifelyWebhookController;
import com.staydesk.exception.UserNotFoundException;
import com.staydesk.model.Account;
import com.staydesk.model.Employee;
import com.staydesk.repository.AccountRepository;
import com.staydesk.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;

    public UserService(AccountRepository accountRepository, EmployeeRepository employeeRepository) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
    }

    public UserResponse getDisplayName(UUID userId) {
        LOGGER.info("Getting display name for userId {}", userId);

        return accountRepository.findById(userId)
                .map(Account::displayName)
                .or(() -> employeeRepository.findById(userId).map(Employee::name))
                .map(UserResponse::new)
                .orElseThrow(UserNotFoundException::new);
    }

    public record UserResponse(String displayName) {
    }
}
