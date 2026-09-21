package com.staydesk.service;

import com.staydesk.model.Account;
import com.staydesk.model.Employee;
import com.staydesk.model.dto.CurrentUserResponse;
import com.staydesk.repository.AccountRepository;
import com.staydesk.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

// Resolves the logged-in user from the JWT subject, which is a Supabase auth user id matching
// either employees.id (username+PIN login) or accounts.id (system admin email+password login).
@Service
public class CurrentUserService {

    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final String appVersion;

    public CurrentUserService(EmployeeRepository employeeRepository, AccountRepository accountRepository,
                              @Value("${app.version}") String appVersion) {
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.appVersion = appVersion;
    }

    public CurrentUserResponse getCurrentUser(UUID id) {
        Optional<Employee> employee = employeeRepository.findById(id);
        if (employee.isPresent()) {
            return new CurrentUserResponse(id, employee.get().name(), employee.get().lastSeenReleaseNotesId(), appVersion);
        }

        Account account = accountRepository.findById(id)
                                           .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No employee or account for this user"));

        return new CurrentUserResponse(id, account.displayName(), account.lastSeenReleaseNotesId(), appVersion);
    }

    public void acknowledgeVersion(UUID id, Integer releaseNotesId) {
        if (employeeRepository.existsById(id)) {
            employeeRepository.updateLastSeenReleaseNotesId(id, releaseNotesId);
            return;
        }

        if (accountRepository.existsById(id)) {
            accountRepository.updateLastSeenReleaseNotesId(id, releaseNotesId);
            return;
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No employee or account for this user");
    }
}
