package com.staydesk.service

import com.staydesk.exception.PayrollSyncException
import com.staydesk.model.Employee
import com.staydesk.model.EncryptedString
import com.staydesk.model.TimesheetReport
import com.staydesk.payroll.quickbooks.QuickBooksClient
import com.staydesk.payroll.quickbooks.dto.QuickBooksTimeActivity
import com.staydesk.repository.EmployeeRepository
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class PayrollSyncServiceSpec extends Specification {

    TimesheetService timesheetService = Mock()
    EmployeeRepository employeeRepository = Mock()
    QuickBooksClient quickBooksClient = Mock()

    PayrollSyncService payrollSyncService = new PayrollSyncService(timesheetService, employeeRepository, quickBooksClient)

    LocalDate startDate = LocalDate.of(2026, 8, 1)
    LocalDate endDate = LocalDate.of(2026, 8, 14)

    private static Employee employee(UUID id, String quickbooksEmployeeId) {
        new Employee(id, new EncryptedString("Jane"), new EncryptedString("Doe"), new EncryptedString("jane@staydesk.com"),
                "hash", "jdoe", 1, BigDecimal.TEN, LocalDate.now(), true, null, Employee.PayRateType.HOURLY, false,
                LocalDateTime.now(), LocalDateTime.now(), quickbooksEmployeeId)
    }

    def "syncs employees that have a QuickBooks mapping and skips those that don't"() {
        given:
        UUID mappedId = UUID.randomUUID()
        UUID unmappedId = UUID.randomUUID()

        def report = new TimesheetReport(startDate, endDate, [
                new TimesheetReport.EmployeeTimesheetRow(mappedId, "Jane Doe", '$10.00/hr', [], BigDecimal.valueOf(37.5)),
                new TimesheetReport.EmployeeTimesheetRow(unmappedId, "John Smith", '$10.00/hr', [], BigDecimal.valueOf(20))
        ], BigDecimal.valueOf(57.5))

        timesheetService.buildReport(startDate, endDate) >> report
        employeeRepository.findAll() >> [employee(mappedId, "qb-emp-1"), employee(unmappedId, null)]

        when:
        def result = payrollSyncService.sync(startDate, endDate)

        then:
        1 * quickBooksClient.pushTimeActivity({ QuickBooksTimeActivity a -> a.employeeRef().value() == "qb-emp-1" })
        result.employeesSynced() == 1
        result.failedEmployees() == ["John Smith (no QuickBooks employee mapping)"]
        result.totalHoursSynced() == BigDecimal.valueOf(57.5)
    }

    def "records a failure rather than aborting the sync when QuickBooks rejects one employee"() {
        given:
        UUID id = UUID.randomUUID()

        def report = new TimesheetReport(startDate, endDate, [
                new TimesheetReport.EmployeeTimesheetRow(id, "Jane Doe", '$10.00/hr', [], BigDecimal.valueOf(37.5))
        ], BigDecimal.valueOf(37.5))

        timesheetService.buildReport(startDate, endDate) >> report
        employeeRepository.findAll() >> [employee(id, "qb-emp-1")]
        quickBooksClient.pushTimeActivity(_) >> { throw new PayrollSyncException("QuickBooks time activity push failed: 401") }

        when:
        def result = payrollSyncService.sync(startDate, endDate)

        then:
        result.employeesSynced() == 0
        result.failedEmployees() == ["Jane Doe (QuickBooks time activity push failed: 401)"]
    }
}
