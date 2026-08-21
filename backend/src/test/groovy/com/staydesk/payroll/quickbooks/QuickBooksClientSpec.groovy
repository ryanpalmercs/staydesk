package com.staydesk.payroll.quickbooks

import com.staydesk.exception.PayrollSyncException
import com.staydesk.payroll.quickbooks.dto.QuickBooksTimeActivity
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.time.LocalDate

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

    def "pushTimeActivity succeeds when QuickBooks accepts the time activity"() {
        given:
        authService.getAccessToken() >> 'fake-token'
        authService.getRealmId() >> '123456789'

        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/v3/company/123456789/timeactivity") { exchange ->
            def bytes = '{"TimeActivity": {"Id": "1"}}'.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
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
}
