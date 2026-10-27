package com.staydesk.payroll.quickbooks

import com.staydesk.exception.PayrollSyncException
import com.staydesk.model.EncryptedToken
import com.staydesk.model.QuickBooksConnection
import com.staydesk.payroll.quickbooks.dto.QuickBooksTokenResponse
import com.staydesk.repository.QuickBooksConnectionRepository
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class QuickBooksAuthServiceSpec extends Specification {

    QuickBooksConnectionRepository connectionRepository = Mock()
    HttpServer server
    String capturedAuthHeader
    String capturedBody

    def cleanup() {
        server?.stop(0)
    }

    private QuickBooksAuthService serviceWithTokenServer() {
        def service = new QuickBooksAuthService(connectionRepository)
        service.tokenUrl = "http://localhost:${server.address.port}/oauth2/token"
        service.clientId = "client-abc"
        service.clientSecret = "secret-xyz"
        return service
    }

    private void startTokenServer(String responseJson) {
        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/oauth2/token") { exchange ->
            capturedAuthHeader = exchange.requestHeaders.getFirst("Authorization")
            capturedBody = exchange.requestBody.getText("UTF-8")
            def bytes = responseJson.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Connection", "close")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()
    }

    private static Map<String, String> parseForm(String body) {
        body.split('&').collectEntries { pair ->
            def parts = pair.split('=', 2)
            [(URLDecoder.decode(parts[0], 'UTF-8')): URLDecoder.decode(parts[1], 'UTF-8')]
        }
    }

    def "exchangeCodeForTokens sends an authorization_code grant with Basic auth"() {
        given:
        startTokenServer('{"access_token":"access-1","token_type":"bearer","expires_in":3600,' +
                '"refresh_token":"refresh-1","x_refresh_token_expires_in":8726400}')
        def service = serviceWithTokenServer()

        when:
        def response = service.exchangeCodeForTokens("auth-code-1", "http://localhost:8080/quickbooks/connect/callback")

        then:
        response.accessToken() == "access-1"
        response.refreshToken() == "refresh-1"
        response.refreshTokenExpiresInSeconds() == 8726400L

        and:
        capturedAuthHeader == "Basic " + Base64.getEncoder().encodeToString("client-abc:secret-xyz".getBytes())

        and:
        def form = parseForm(capturedBody)
        form.grant_type == "authorization_code"
        form.code == "auth-code-1"
        form.redirect_uri == "http://localhost:8080/quickbooks/connect/callback"
    }

    def "saveConnection persists the connection and caches the access token"() {
        given:
        def service = new QuickBooksAuthService(connectionRepository)
        def tokens = new QuickBooksTokenResponse("access-1", "bearer", 3600, "refresh-1", 8726400L)

        when:
        service.saveConnection("realm-1", tokens)

        then:
        1 * connectionRepository.deleteAll()
        1 * connectionRepository.save({ QuickBooksConnection c ->
            c.realmId() == "realm-1" && c.refreshToken().value() == "refresh-1"
        })

        when:
        def accessToken = service.getAccessToken()

        then:
        accessToken == "access-1"
        0 * connectionRepository.findFirst()
    }

    def "getAccessToken throws when no QuickBooks account is connected"() {
        given:
        def service = new QuickBooksAuthService(connectionRepository)
        connectionRepository.findFirst() >> Optional.empty()

        when:
        service.getAccessToken()

        then:
        thrown(PayrollSyncException)
    }

    def "getAccessToken throws when the stored refresh token has expired"() {
        given:
        def service = new QuickBooksAuthService(connectionRepository)
        def expiredConnection = new QuickBooksConnection(1, "realm-1", new EncryptedToken("refresh-1"),
                LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(101))
        connectionRepository.findFirst() >> Optional.of(expiredConnection)

        when:
        service.getAccessToken()

        then:
        def ex = thrown(PayrollSyncException)
        ex.message.contains("reconnect")
    }

    def "getAccessToken refreshes an expired access token and persists the rotated refresh token"() {
        given:
        startTokenServer('{"access_token":"access-2","token_type":"bearer","expires_in":3600,' +
                '"refresh_token":"refresh-2","x_refresh_token_expires_in":8726400}')
        def service = serviceWithTokenServer()
        def existingConnection = new QuickBooksConnection(1, "realm-1", new EncryptedToken("refresh-1"),
                LocalDateTime.now().plusDays(50), LocalDateTime.now().minusDays(10))
        connectionRepository.findFirst() >> Optional.of(existingConnection)

        when:
        def accessToken = service.getAccessToken()

        then:
        accessToken == "access-2"

        and:
        def form = parseForm(capturedBody)
        form.grant_type == "refresh_token"
        form.refresh_token == "refresh-1"

        and:
        1 * connectionRepository.save({ QuickBooksConnection c ->
            c.id() == 1 && c.realmId() == "realm-1" && c.refreshToken().value() == "refresh-2"
        })
    }

    def "getRealmId returns the connected realm id"() {
        given:
        def service = new QuickBooksAuthService(connectionRepository)
        def connection = new QuickBooksConnection(1, "realm-1", new EncryptedToken("refresh-1"),
                LocalDateTime.now().plusDays(50), LocalDateTime.now())
        connectionRepository.findFirst() >> Optional.of(connection)

        expect:
        service.getRealmId() == "realm-1"
    }

    def "getRealmId throws when no QuickBooks account is connected"() {
        given:
        def service = new QuickBooksAuthService(connectionRepository)
        connectionRepository.findFirst() >> Optional.empty()

        when:
        service.getRealmId()

        then:
        thrown(PayrollSyncException)
    }

    def "getStatus reflects an active connection"() {
        given:
        def service = new QuickBooksAuthService(connectionRepository)
        def connectedAt = LocalDateTime.now().minusDays(5)
        def expiresAt = LocalDateTime.now().plusDays(50)
        def connection = new QuickBooksConnection(1, "realm-1", new EncryptedToken("refresh-1"), expiresAt, connectedAt)
        connectionRepository.findFirst() >> Optional.of(connection)

        when:
        def status = service.getStatus()

        then:
        status.connected()
        status.realmId() == "realm-1"
        status.connectedAt() == connectedAt
        status.refreshTokenExpiresAt() == expiresAt
    }

    def "getStatus reflects no connection"() {
        given:
        def service = new QuickBooksAuthService(connectionRepository)
        connectionRepository.findFirst() >> Optional.empty()

        when:
        def status = service.getStatus()

        then:
        !status.connected()
        status.realmId() == null
    }

    def "disconnect deletes the stored connection and clears the cached token"() {
        given:
        def service = new QuickBooksAuthService(connectionRepository)
        connectionRepository.findFirst() >> Optional.empty()
        service.saveConnection("realm-1", new QuickBooksTokenResponse("access-1", "bearer", 3600, "refresh-1", 8726400L))

        when:
        service.disconnect()

        then:
        1 * connectionRepository.deleteAll()

        when:
        service.getAccessToken()

        then:
        thrown(PayrollSyncException)
    }
}