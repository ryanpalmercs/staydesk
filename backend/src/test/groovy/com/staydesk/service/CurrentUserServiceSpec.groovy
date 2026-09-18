package com.staydesk.service

import com.staydesk.model.Account
import com.staydesk.model.Employee
import com.staydesk.model.EncryptedString
import com.staydesk.repository.AccountRepository
import com.staydesk.repository.EmployeeRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class CurrentUserServiceSpec extends Specification {

    EmployeeRepository employeeRepository = Mock()
    AccountRepository accountRepository = Mock()

    CurrentUserService currentUserService = new CurrentUserService(employeeRepository, accountRepository, "abc123")

    private static Employee employee(UUID id, String lastSeenAppVersion = null) {
        new Employee(id, new EncryptedString("Jane"), new EncryptedString("Doe"),
                new EncryptedString("jane@staydesk.com"), "hash", "jdoe", 1, BigDecimal.TEN,
                LocalDate.now(), true, null, Employee.PayRateType.HOURLY, false,
                LocalDateTime.now(), LocalDateTime.now(), lastSeenAppVersion)
    }

    private static Account account(UUID id, String lastSeenAppVersion = null) {
        new Account(id, Account.AccountKind.SYSTEM_ADMIN, "Ryan Palmer", true,
                LocalDateTime.now(), LocalDateTime.now(), lastSeenAppVersion)
    }

    def "getCurrentUser returns an employee's name and last-seen version alongside the current app version"() {
        given:
        UUID id = UUID.randomUUID()
        employeeRepository.findById(id) >> Optional.of(employee(id, "old-sha"))

        when:
        def result = currentUserService.getCurrentUser(id)

        then:
        result.id() == id
        result.displayName() == "Jane Doe"
        result.lastSeenAppVersion() == "old-sha"
        result.currentAppVersion() == "abc123"
        0 * accountRepository.findById(_)
    }

    def "getCurrentUser falls back to accounts when no employee matches the id"() {
        given:
        UUID id = UUID.randomUUID()
        employeeRepository.findById(id) >> Optional.empty()
        accountRepository.findById(id) >> Optional.of(account(id, "old-sha"))

        when:
        def result = currentUserService.getCurrentUser(id)

        then:
        result.displayName() == "Ryan Palmer"
        result.lastSeenAppVersion() == "old-sha"
        result.currentAppVersion() == "abc123"
    }

    def "getCurrentUser throws 404 when the id matches neither an employee nor an account"() {
        given:
        UUID id = UUID.randomUUID()
        employeeRepository.findById(id) >> Optional.empty()
        accountRepository.findById(id) >> Optional.empty()

        when:
        currentUserService.getCurrentUser(id)

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.NOT_FOUND
    }

    def "acknowledgeVersion updates the employee's last-seen version when the id is an employee"() {
        given:
        UUID id = UUID.randomUUID()
        employeeRepository.existsById(id) >> true

        when:
        currentUserService.acknowledgeVersion(id)

        then:
        1 * employeeRepository.updateLastSeenAppVersion(id, "abc123")
        0 * accountRepository.updateLastSeenAppVersion(_, _)
    }

    def "acknowledgeVersion updates the account's last-seen version when the id is not an employee"() {
        given:
        UUID id = UUID.randomUUID()
        employeeRepository.existsById(id) >> false
        accountRepository.existsById(id) >> true

        when:
        currentUserService.acknowledgeVersion(id)

        then:
        1 * accountRepository.updateLastSeenAppVersion(id, "abc123")
    }

    def "acknowledgeVersion throws 404 when the id matches neither an employee nor an account"() {
        given:
        UUID id = UUID.randomUUID()
        employeeRepository.existsById(id) >> false
        accountRepository.existsById(id) >> false

        when:
        currentUserService.acknowledgeVersion(id)

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.NOT_FOUND
    }
}
