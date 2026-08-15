package com.staydesk.service

import com.staydesk.exception.EmployeeAlreadyExistsException
import com.staydesk.model.Account
import com.staydesk.model.AuthRole
import com.staydesk.model.Employee
import com.staydesk.model.EmployeeType
import com.staydesk.model.EncryptedString
import com.staydesk.model.request.CreateEmployeeRequest
import com.staydesk.repository.EmployeeRepository
import com.staydesk.repository.EmployeeTypeRepository
import com.staydesk.security.PiiCipher
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class EmployeeServiceSpec extends Specification {

    EmployeeRepository employeeRepository = Mock()
    EmployeeTypeRepository employeeTypeRepository = Mock()
    SupabaseAdminClient supabaseAdminClient = Mock()
    JdbcAggregateTemplate jdbcAggregateTemplate = Mock()
    StaffDoorAccessService staffDoorAccessService = Mock()
    PiiCipher piiCipher = Mock()

    EmployeeService employeeService = new EmployeeService(employeeRepository, employeeTypeRepository,
            supabaseAdminClient, jdbcAggregateTemplate, staffDoorAccessService, piiCipher)

    // "482913" is a valid 6-digit PIN under PasscodeRules (not ascending/descending/repeated) --
    // load-bearing for every happy-path test below.
    private static CreateEmployeeRequest request(boolean grantDoorAccess = false, String pin = "482913",
                                                  String email = "jane@staydesk.com") {
        new CreateEmployeeRequest("Jane", "Doe", email, "jdoe", 1, BigDecimal.valueOf(20),
                LocalDate.of(2026, 1, 1), pin, null, Employee.PayRateType.HOURLY, grantDoorAccess)
    }

    private static Employee existingEmployee() {
        new Employee(UUID.randomUUID(), new EncryptedString("Existing"), new EncryptedString("Employee"),
                new EncryptedString("existing@staydesk.com"), "existing-hash", "existing", 1, BigDecimal.TEN,
                LocalDate.now(), true, null, Employee.PayRateType.HOURLY, false, LocalDateTime.now(), LocalDateTime.now(), null)
    }

    def "throws EmployeeAlreadyExistsException when the username is already taken"() {
        given:
        employeeRepository.findByUsername("jdoe") >> Optional.of(existingEmployee())

        when:
        employeeService.createEmployee(request())

        then:
        thrown(EmployeeAlreadyExistsException)
        0 * piiCipher.hash(_)
        0 * employeeTypeRepository.findById(_)
        0 * supabaseAdminClient.createUser(*_)
        0 * jdbcAggregateTemplate.insert(_)
    }

    def "throws EmployeeAlreadyExistsException when the email hash is already taken, after hashing the stripped/lower-cased email"() {
        given:
        employeeRepository.findByUsername(_) >> Optional.empty()
        employeeRepository.findByEmailHash("hash123") >> Optional.of(existingEmployee())

        when:
        employeeService.createEmployee(request(false, "482913", " Jane@Staydesk.com "))

        then:
        thrown(EmployeeAlreadyExistsException)
        1 * piiCipher.hash("jane@staydesk.com") >> "hash123"
        0 * employeeTypeRepository.findById(_)
        0 * supabaseAdminClient.createUser(*_)
        0 * jdbcAggregateTemplate.insert(_)
    }

    def "throws a 400 ResponseStatusException when the employee type does not exist"() {
        given:
        employeeRepository.findByUsername(_) >> Optional.empty()
        piiCipher.hash(_) >> "hash123"
        employeeRepository.findByEmailHash(_) >> Optional.empty()
        employeeTypeRepository.findById(1) >> Optional.empty()

        when:
        employeeService.createEmployee(request())

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.BAD_REQUEST
        0 * supabaseAdminClient.createUser(*_)
        0 * jdbcAggregateTemplate.insert(_)
    }

    def "throws a 400 ResponseStatusException when grantDoorAccess is true and the PIN is invalid"() {
        given:
        employeeRepository.findByUsername(_) >> Optional.empty()
        piiCipher.hash(_) >> "hash123"
        employeeRepository.findByEmailHash(_) >> Optional.empty()
        employeeTypeRepository.findById(1) >> Optional.of(new EmployeeType(1, "Front Desk", AuthRole.FRONT_DESK, true))

        when:
        // "111111" is a repeated-digit PIN, rejected by PasscodeRules
        employeeService.createEmployee(request(true, "111111"))

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.BAD_REQUEST
        0 * supabaseAdminClient.createUser(*_)
        0 * jdbcAggregateTemplate.insert(_)
        0 * staffDoorAccessService.grantAccess(*_)
    }

    def "throws EmployeeAlreadyExistsException and inserts nothing when Supabase responds 422 Unprocessable Entity"() {
        given:
        employeeRepository.findByUsername(_) >> Optional.empty()
        piiCipher.hash(_) >> "hash123"
        employeeRepository.findByEmailHash(_) >> Optional.empty()
        employeeTypeRepository.findById(1) >> Optional.of(new EmployeeType(1, "Front Desk", AuthRole.FRONT_DESK, true))
        supabaseAdminClient.createUser(*_) >> { throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY) }

        when:
        employeeService.createEmployee(request())

        then:
        thrown(EmployeeAlreadyExistsException)
        0 * jdbcAggregateTemplate.insert(_)
        0 * staffDoorAccessService.grantAccess(*_)
    }

    def "rethrows the original HttpClientErrorException for non-422 Supabase failures and inserts nothing"() {
        given:
        employeeRepository.findByUsername(_) >> Optional.empty()
        piiCipher.hash(_) >> "hash123"
        employeeRepository.findByEmailHash(_) >> Optional.empty()
        employeeTypeRepository.findById(1) >> Optional.of(new EmployeeType(1, "Front Desk", AuthRole.FRONT_DESK, true))
        supabaseAdminClient.createUser(*_) >> { throw new HttpClientErrorException(HttpStatus.BAD_GATEWAY) }

        when:
        employeeService.createEmployee(request())

        then:
        def ex = thrown(HttpClientErrorException)
        ex.statusCode == HttpStatus.BAD_GATEWAY
        0 * jdbcAggregateTemplate.insert(_)
    }

    def "creates an employee: inserts the Account before the Employee, grants no door access, and returns the saved Employee"() {
        given:
        def req = request(false)
        def supabaseId = UUID.randomUUID()
        def type = new EmployeeType(1, "Front Desk", AuthRole.FRONT_DESK, true)

        employeeRepository.findByUsername(_) >> Optional.empty()
        piiCipher.hash(_) >> "hash123"
        employeeRepository.findByEmailHash(_) >> Optional.empty()
        employeeTypeRepository.findById(1) >> Optional.of(type)
        supabaseAdminClient.createUser(req.email(), req.pin(), "FRONT_DESK") >> supabaseId

        when:
        def result = employeeService.createEmployee(req)

        then: "the Account is inserted first"
        1 * jdbcAggregateTemplate.insert({
            it instanceof Account && it.id() == supabaseId && it.kind() == Account.AccountKind.EMPLOYEE &&
                    it.displayName() == null && it.active()
        }) >> { args -> args[0] }

        then: "the Employee is inserted after the Account"
        1 * jdbcAggregateTemplate.insert({
            it instanceof Employee && it.id() == supabaseId && it.username() == "jdoe" && !it.doorAccessEnabled()
        }) >> { args -> args[0] }

        then: "door access is never granted"
        0 * staffDoorAccessService.grantAccess(*_)
        result.id() == supabaseId
        result.username() == "jdoe"
    }

    def "creates an employee with door access: inserts Account, then Employee, then grants door access with the saved employee and requested PIN"() {
        given:
        def req = request(true, "482913")
        def supabaseId = UUID.randomUUID()
        def type = new EmployeeType(1, "Front Desk", AuthRole.FRONT_DESK, true)

        employeeRepository.findByUsername(_) >> Optional.empty()
        piiCipher.hash(_) >> "hash123"
        employeeRepository.findByEmailHash(_) >> Optional.empty()
        employeeTypeRepository.findById(1) >> Optional.of(type)
        supabaseAdminClient.createUser(req.email(), "482913", "FRONT_DESK") >> supabaseId

        when:
        def result = employeeService.createEmployee(req)

        then: "the Account is inserted first"
        1 * jdbcAggregateTemplate.insert({
            it instanceof Account && it.id() == supabaseId && it.kind() == Account.AccountKind.EMPLOYEE
        }) >> { args -> args[0] }

        then: "the Employee is inserted after the Account, with door access enabled"
        1 * jdbcAggregateTemplate.insert({
            it instanceof Employee && it.id() == supabaseId && it.doorAccessEnabled()
        }) >> { args -> args[0] }

        then: "door access is granted last, using the saved employee and requested PIN"
        1 * staffDoorAccessService.grantAccess({
            it instanceof Employee && it.id() == supabaseId && it.doorAccessEnabled()
        }, "482913")
        result.doorAccessEnabled()
    }
}
