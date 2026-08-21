package com.staydesk.service

import com.staydesk.exception.PayrollSyncException
import com.staydesk.model.ContactInfo
import com.staydesk.model.Employee
import com.staydesk.model.EncryptedString
import com.staydesk.payroll.quickbooks.QuickBooksClient
import com.staydesk.repository.EmployeeRepository
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class QuickBooksEmployeeSyncServiceSpec extends Specification {

    EmployeeRepository employeeRepository = Mock()
    QuickBooksClient quickBooksClient = Mock()

    QuickBooksEmployeeSyncService service = new QuickBooksEmployeeSyncService(employeeRepository, quickBooksClient)

    private static Employee employee(String quickbooksEmployeeId = null) {
        new Employee(UUID.randomUUID(), new EncryptedString("Jane"), new EncryptedString("Doe"),
                new EncryptedString("jane@staydesk.com"), "hash123", "jdoe", 1, BigDecimal.valueOf(20),
                LocalDate.of(2026, 1, 1), true, new ContactInfo("5551234567", "123 Main St", null, "Springfield", "MO", "65801"),
                Employee.PayRateType.HOURLY, false, LocalDateTime.now(), LocalDateTime.now(), quickbooksEmployeeId)
    }

    def "syncAll creates unmapped employees not found in QuickBooks and writes back the new id"() {
        given:
        def emp = employee()
        employeeRepository.findAll() >> [emp]
        quickBooksClient.findEmployeeByDisplayName(emp.name()) >> Optional.empty()
        quickBooksClient.createEmployee(emp) >> "qb-new-1"

        when:
        def result = service.syncAll()

        then:
        1 * employeeRepository.updateQuickbooksEmployeeId(emp.id(), "qb-new-1")
        result.created() == [emp.name()]
        result.matched() == []
        result.failed() == []
    }

    def "syncAll matches an unmapped employee already found in QuickBooks and writes back its id"() {
        given:
        def emp = employee()
        employeeRepository.findAll() >> [emp]
        quickBooksClient.findEmployeeByDisplayName(emp.name()) >> Optional.of("qb-existing-1")

        when:
        def result = service.syncAll()

        then:
        0 * quickBooksClient.createEmployee(_)
        1 * employeeRepository.updateQuickbooksEmployeeId(emp.id(), "qb-existing-1")
        result.matched() == [emp.name()]
        result.created() == []
    }

    def "syncAll updates an already-mapped employee instead of searching by name"() {
        given:
        def emp = employee("qb-42")
        employeeRepository.findAll() >> [emp]

        when:
        def result = service.syncAll()

        then:
        1 * quickBooksClient.updateEmployee(emp)
        0 * quickBooksClient.findEmployeeByDisplayName(_)
        0 * quickBooksClient.createEmployee(_)
        0 * employeeRepository.updateQuickbooksEmployeeId(*_)
        result.matched() == [emp.name()]
    }

    def "syncAll records a failure without throwing when QuickBooks calls fail"() {
        given:
        def emp = employee()
        employeeRepository.findAll() >> [emp]
        quickBooksClient.findEmployeeByDisplayName(emp.name()) >> { throw new PayrollSyncException("boom") }

        when:
        def result = service.syncAll()

        then:
        noExceptionThrown()
        result.failed().size() == 1
        result.failed()[0].contains(emp.name())
        result.failed()[0].contains("boom")
    }

    def "syncOne creates a single employee best-effort"() {
        given:
        def emp = employee()
        quickBooksClient.findEmployeeByDisplayName(emp.name()) >> Optional.empty()
        quickBooksClient.createEmployee(emp) >> "qb-new-2"

        when:
        service.syncOne(emp)

        then:
        1 * employeeRepository.updateQuickbooksEmployeeId(emp.id(), "qb-new-2")
    }

    def "syncOne never throws even when QuickBooks is unreachable"() {
        given:
        def emp = employee()
        quickBooksClient.findEmployeeByDisplayName(_) >> { throw new PayrollSyncException("no connection") }

        when:
        service.syncOne(emp)

        then:
        noExceptionThrown()
        0 * employeeRepository.updateQuickbooksEmployeeId(*_)
    }

    def "syncActiveStatus does nothing when the employee has no QuickBooks mapping"() {
        when:
        service.syncActiveStatus(null, false)

        then:
        0 * quickBooksClient.setEmployeeActive(*_)
    }

    def "syncActiveStatus pushes the active flag for a mapped employee"() {
        when:
        service.syncActiveStatus("qb-42", false)

        then:
        1 * quickBooksClient.setEmployeeActive("qb-42", false)
    }

    def "syncActiveStatus swallows failures rather than throwing"() {
        given:
        quickBooksClient.setEmployeeActive("qb-42", false) >> { throw new PayrollSyncException("boom") }

        when:
        service.syncActiveStatus("qb-42", false)

        then:
        noExceptionThrown()
    }
}
