package com.staydesk.payment.elavon

import com.sun.net.httpserver.HttpServer
import spock.lang.Specification

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

class ElavonCpiClientSpec extends Specification {

    ElavonCpiAuthService authService = Mock()
    HttpServer server

    def cleanup() {
        server?.stop(0)
    }

    private ElavonCpiClient clientPointedAt(String baseUrl) {
        def client = new ElavonCpiClient(authService)
        client.baseUrl = baseUrl
        return client
    }

    def "healthCheck returns true when CPI reports the device is available"() {
        given:
        authService.getAccessToken() >> 'fake-token'

        def responseBody = '''
        {
          "responseChannelType": "SYNCHRONOUS",
          "responseChannel": { "responsePayloadFormat": "DEVICEMESSAGE" },
          "messageId": "VMH1sJKCiEaRStqV",
          "messageType": "application/vnd.elavon.transaction-b.v1+json",
          "message": {
            "referenceNumber": "10234583",
            "transType": "HEALTHCHECK",
            "response": { "responseCode": "0000", "responseText": "SUCCESSFUL" }
          }
        }
        '''

        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/devices/device-1/message") { exchange ->
            def bytes = responseBody.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")

        expect:
        client.healthCheck("device-1")
    }

    def "healthCheck returns false rather than throwing when the device/gateway is unreachable"() {
        given:
        authService.getAccessToken() >> 'fake-token'

        // Bind and immediately release a port so nothing is listening on it -
        // simulates a genuinely offline terminal/gateway (connection refused).
        def closedPort = new ServerSocket(0).withCloseable { it.localPort }

        def client = clientPointedAt("http://localhost:${closedPort}")

        expect:
        !client.healthCheck("device-1")
    }

    def "healthCheck returns false when CPI responds with a non-success code"() {
        given:
        authService.getAccessToken() >> 'fake-token'

        def responseBody = '''
        {
          "responseChannelType": "SYNCHRONOUS",
          "responseChannel": { "responsePayloadFormat": "DEVICEMESSAGE" },
          "messageId": "VMH1sJKCiEaRStqV",
          "messageType": "application/vnd.elavon.transaction-b.v1+json",
          "message": {
            "referenceNumber": "10234583",
            "transType": "HEALTHCHECK",
            "response": { "responseCode": "9999", "responseText": "DEVICE OFFLINE" }
          }
        }
        '''

        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/devices/device-1/message") { exchange ->
            def bytes = responseBody.getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.responseBody.write(bytes)
            exchange.responseBody.close()
        }
        server.start()

        def client = clientPointedAt("http://localhost:${server.address.port}")

        expect:
        !client.healthCheck("device-1")
    }
}
