package com.staydesk.payroll.quickbooks

import com.staydesk.exception.PayrollSyncException
import com.staydesk.model.ContactInfo
import com.staydesk.model.Employee
import com.staydesk.model.EncryptedString
import com.staydesk.payroll.quickbooks.dto.QuickBooksTimeActivity
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime

class QuickBooksClientSpec extends Specification {

    QuickBooksAuthService authService = Mock()
    HttpServer server

    def cleanup() {
        server?.stop(0)
    }

    private QuickBooksClient clientPointedAt(String baseUrl) {
        def client = new QuickBooksClient(authService)
        client.baseUrl = baseUrl
        return client
    }

    private static Employee testEmployee(String quickbooksEmployeeId = null) {
        new Employee(UUID.randomUUID(), new EncryptedString("Jane"), new EncryptedString("Doe"),
                new EncryptedString("jane@staydesk.com"), "hash123", "jdoe", 1, BigDecimal.valueOf(20),
                LocalDate.of(2026, 1, 1), true,
                new ContactInfo("5551234567", "123 Main St", null, "Springfield", "MO", "65801"),
                Employee.PayRateType.HOURLY, false, LocalDateTime.now(), LocalDateTime.now(), quickbooksEmployeeId)
    }

    def "pushTimeActivity succeeds when QuickBooks accepts the time activity"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/v3/company/123456789/timeactivity") { exchange ->
            def bytes = '{"TimeActivity": {"Id": "1"}}'.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Connection", "close")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")
        def activity = QuickBooksTimeActivity.forPeriod("qb-emp-1", LocalDate.of(2026, 8, 14),
                BigDecimal.valueOf(37.5), "Pay period 2026-08-01 to 2026-08-14")

        when:
        client.pushTimeActivity(activity)

        then:
        noExceptionThrown()
    }

    def "pushTimeActivity wraps a connection failure as a PayrollSyncException"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        def closedPort = new ServerSocket(0).withCloseable { it.localPort }
        def client = clientPointedAt("http://localhost:${closedPort}")
        def activity = QuickBooksTimeActivity.forPeriod("qb-emp-1", LocalDate.of(2026, 8, 14),
                BigDecimal.valueOf(37.5), "Pay period 2026-08-01 to 2026-08-14")

        when:
        client.pushTimeActivity(activity)

        then:
        thrown(PayrollSyncException)
    }

    def "findEmployeeByDisplayName returns the id when a match exists"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/v3/company/123456789/query") { exchange ->
            def bytes = '{"QueryResponse":{"Employee":[{"Id":"qb-42","DisplayName":"Jane Doe"}]}}'.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Connection", "close")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")

        when:
        def result = client.findEmployeeByDisplayName("Jane Doe")

        then:
        result == Optional.of("qb-42")
    }

    def "findEmployeeByDisplayName returns empty when QuickBooks has no match"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/v3/company/123456789/query") { exchange ->
            def bytes = '{"QueryResponse":{}}'.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Connection", "close")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")

        when:
        def result = client.findEmployeeByDisplayName("Nobody Here")

        then:
        result == Optional.empty()
    }

    def "createEmployee posts the employee fields and returns the new QuickBooks id"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        def capturedBody = null
        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/v3/company/123456789/employee") { exchange ->
            capturedBody = exchange.requestBody.getText("UTF-8")
            def bytes = '{"Employee":{"Id":"qb-99"}}'.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Connection", "close")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")

        when:
        def result = client.createEmployee(testEmployee())

        then:
        result == "qb-99"
        capturedBody.contains('"GivenName":"Jane"')
        capturedBody.contains('"FamilyName":"Doe"')
        capturedBody.contains('"EmployeeNumber":"jdoe"')
        capturedBody.contains('"BillRate":20')
        capturedBody.contains('"PrimaryEmailAddr":{"Address":"jane@staydesk.com"}')
    }

    def "createEmployee wraps a connection failure as a PayrollSyncException"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        def closedPort = new ServerSocket(0).withCloseable { it.localPort }
        def client = clientPointedAt("http://localhost:${closedPort}")

        when:
        client.createEmployee(testEmployee())

        then:
        thrown(PayrollSyncException)
    }

    def "getEmployee returns the employee for a valid id"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/v3/company/123456789/employee/qb-42") { exchange ->
            def bytes = '{"Employee":{"Id":"qb-42","SyncToken":"3","DisplayName":"Jane Doe"}}'.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Connection", "close")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")

        when:
        def result = client.getEmployee("qb-42")

        then:
        result.id() == "qb-42"
        result.syncToken() == "3"
    }

    def "getEmployee wraps a connection failure as a PayrollSyncException"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        def closedPort = new ServerSocket(0).withCloseable { it.localPort }
        def client = clientPointedAt("http://localhost:${closedPort}")

        when:
        client.getEmployee("qb-42")

        then:
        thrown(PayrollSyncException)
    }

    def "updateEmployee fetches the current SyncToken and posts a sparse update"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        def capturedBody = null
        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/v3/company/123456789/employee/qb-42") { exchange ->
            def bytes = '{"Employee":{"Id":"qb-42","SyncToken":"5"}}'.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Connection", "close")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.createContext("/v3/company/123456789/employee") { exchange ->
            if (exchange.requestMethod == "POST") {
                capturedBody = exchange.requestBody.getText("UTF-8")
                def bytes = '{"Employee":{"Id":"qb-42","SyncToken":"6"}}'.getBytes(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.responseHeaders.add("Connection", "close")
                exchange.sendResponseHeaders(200, bytes.length)
                exchange.responseBody.write(bytes)
                exchange.responseBody.close()
            }
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")

        when:
        client.updateEmployee(testEmployee("qb-42"))

        then:
        capturedBody.contains('"Id":"qb-42"')
        capturedBody.contains('"SyncToken":"5"')
        capturedBody.contains('"sparse":true')
    }

    def "updateEmployee wraps a connection failure as a PayrollSyncException"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        def closedPort = new ServerSocket(0).withCloseable { it.localPort }
        def client = clientPointedAt("http://localhost:${closedPort}")

        when:
        client.updateEmployee(testEmployee("qb-42"))

        then:
        thrown(PayrollSyncException)
    }

    def "setEmployeeActive fetches the current SyncToken and posts the active flag as a sparse update"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        def capturedBody = null
        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/v3/company/123456789/employee/qb-42") { exchange ->
            def bytes = '{"Employee":{"Id":"qb-42","SyncToken":"2"}}'.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Connection", "close")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.createContext("/v3/company/123456789/employee") { exchange ->
            if (exchange.requestMethod == "POST") {
                capturedBody = exchange.requestBody.getText("UTF-8")
                def bytes = '{"Employee":{"Id":"qb-42","SyncToken":"3"}}'.getBytes(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.responseHeaders.add("Connection", "close")
                exchange.sendResponseHeaders(200, bytes.length)
                exchange.responseBody.write(bytes)
                exchange.responseBody.close()
            }
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")

        when:
        client.setEmployeeActive("qb-42", false)

        then:
        capturedBody.contains('"Active":false')
        capturedBody.contains('"SyncToken":"2"')
    }

    def "setEmployeeActive wraps a connection failure as a PayrollSyncException"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        def closedPort = new ServerSocket(0).withCloseable { it.localPort }
        def client = clientPointedAt("http://localhost:${closedPort}")

        when:
        client.setEmployeeActive("qb-42", false)

        then:
        thrown(PayrollSyncException)
    }
}
