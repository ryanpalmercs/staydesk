package com.staydesk.bridgeagent

import spock.lang.Specification

class ReconnectBackoffSpec extends Specification {

    def "current delay starts at the initial value"() {
        given:
        def backoff = new ReconnectBackoff()

        expect:
        backoff.currentDelay() == "5s"
    }

    def "delay doubles after each scheduled reconnect"() {
        given:
        def backoff = new ReconnectBackoff()

        when:
        backoff.scheduleReconnect({})

        then:
        backoff.currentDelay() == "10s"

        when:
        backoff.scheduleReconnect({})

        then:
        backoff.currentDelay() == "20s"
    }

    def "delay caps at the maximum"() {
        given:
        def backoff = new ReconnectBackoff()

        when:
        10.times { backoff.scheduleReconnect({}) }

        then:
        backoff.currentDelay() == "60s"
    }

    def "reset returns to the initial delay"() {
        given:
        def backoff = new ReconnectBackoff()

        when:
        backoff.scheduleReconnect({})
        backoff.reset()

        then:
        backoff.currentDelay() == "5s"
    }
}
