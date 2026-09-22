package com.staydesk.payment.ingenico

import com.fasterxml.jackson.databind.ObjectMapper
import com.staydesk.bridge.TerminalBridgeSessionRegistry
import com.staydesk.exception.TerminalBridgeException
import com.staydesk.model.TerminalTransaction
import com.staydesk.repository.TerminalTransactionRepository
import spock.lang.Specification

class IngenicoBridgeClientSpec extends Specification {

    TerminalBridgeSessionRegistry sessionRegistry = Mock()
    TerminalTransactionRepository transactionRepository = Mock()
    ObjectMapper objectMapper = new ObjectMapper()

    IngenicoBridgeClient bridgeClient = new IngenicoBridgeClient(sessionRegistry, transactionRepository, objectMapper)

    // Requests are sent synchronously but the correlated response only arrives via
    // completeFlow() - simulate the bridge agent by replying from another thread as
    // soon as the (only) outbound request carrying a flow_id is sent.
    private void respondWhenRequestSent(String eventJson) {
        sessionRegistry.send(_ as String) >> { String json ->
            def node = objectMapper.readTree(json)

            if (node.has("request")) {
                String flowId = node.at("/request/flow_id").asText()
                Thread.start { bridgeClient.completeFlow(flowId, objectMapper.readTree(eventJson)) }
            }

            null
        }
    }

    def "sendTransaction returns the terminal's result and persists the flow"() {
        given:
        respondWhenRequestSent('''
            {"status":"completed","results":[{"status":"approved","reference_no":"ref-1",
            "authorization_no":"AUTH123","host_response_text":"APPROVED"}]}
        ''')

        when:
        def result = bridgeClient.sendTransaction(TerminalTransaction.Operation.SALE, null, "sale",
                BigDecimal.valueOf(64.17), null)

        then:
        result.status() == "approved"
        result.referenceNumber() == "ref-1"

        and:
        2 * transactionRepository.save({ TerminalTransaction t -> t.operation() == TerminalTransaction.Operation.SALE }) >>
                { TerminalTransaction t -> t }
    }

    def "sendTransaction promotes reference_no, authorization_no, card_last4 and host_response_text onto the saved row"() {
        given:
        respondWhenRequestSent('''
            {"status":"completed","results":[{"status":"approved","reference_no":"ref-1",
            "authorization_no":"AUTH123","host_response_text":"APPROVED","card":{"account_no":"************2205"}}]}
        ''')

        when:
        def result = bridgeClient.sendTransaction(TerminalTransaction.Operation.SALE, null, "sale", BigDecimal.valueOf(64.17), null)

        then:
        result.status() == "approved"
        1 * transactionRepository.save({ TerminalTransaction tt -> tt.status() == TerminalTransaction.Status.PENDING }) >>
                { TerminalTransaction t -> t }
        1 * transactionRepository.save({ TerminalTransaction tt ->
            tt.status() == TerminalTransaction.Status.COMPLETED &&
                    tt.referenceNo() == "ref-1" &&
                    tt.authorizationNo() == "AUTH123" &&
                    tt.cardLast4() == "2205" &&
                    tt.hostResponseText() == "APPROVED"
        }) >> { TerminalTransaction t -> t }
    }

    def "sendTransaction marks the row FAILED without structured fields when the terminal declines"() {
        given:
        respondWhenRequestSent('''
            {"status":"completed","results":[{"status":"decline_by_host_or_card","reference_no":null,
            "authorization_no":null,"host_response_text":"DECLINED"}]}
        ''')

        when:
        def result = bridgeClient.sendTransaction(TerminalTransaction.Operation.SALE, null, "sale", BigDecimal.valueOf(64.17), null)

        then:
        result.status() == "decline_by_host_or_card"
        1 * transactionRepository.save({ TerminalTransaction tt -> tt.status() == TerminalTransaction.Status.PENDING }) >>
                { TerminalTransaction t -> t }
        1 * transactionRepository.save({ TerminalTransaction tt ->
            tt.status() == TerminalTransaction.Status.FAILED && tt.hostResponseText() == "DECLINED"
        }) >> { TerminalTransaction t -> t }
    }

    def "sendTransaction throws when the terminal event carries no results"() {
        given:
        respondWhenRequestSent('{"status":"completed","results":[]}')
        transactionRepository.save(_ as TerminalTransaction) >> { TerminalTransaction t -> t }

        when:
        bridgeClient.sendTransaction(TerminalTransaction.Operation.VOID, null, "void", null, "ref-1")

        then:
        thrown(TerminalBridgeException)
    }

    def "sendSettlement returns the terminal's batch totals and persists the flow as a SETTLEMENT operation"() {
        given:
        respondWhenRequestSent('''
            {"status":"completed","results":[{"status":"approved","terminal_id":"001","merchant_id":"VISAMID0123",
            "terminal_total":{"sale_count":12,"sale_amount":"123456","refund_count":1,"refund_amount":"2000",
            "void_count":0,"void_amount":"0"}}]}
        ''')

        when:
        def result = bridgeClient.sendSettlement()

        then:
        result.status() == "approved"
        result.terminalId() == "001"
        result.merchantId() == "VISAMID0123"
        result.terminalTotal().saleCount() == 12
        result.terminalTotal().saleAmountCents() == 123456L
        result.terminalTotal().refundCount() == 1
        result.terminalTotal().refundAmountCents() == 2000L
        result.terminalTotal().voidCount() == 0
        result.terminalTotal().voidAmountCents() == 0L

        and:
        2 * transactionRepository.save({ TerminalTransaction t -> t.operation() == TerminalTransaction.Operation.SETTLEMENT }) >>
                { TerminalTransaction t -> t }
    }

    def "sendSettlement throws when the terminal event carries no results"() {
        given:
        respondWhenRequestSent('{"status":"completed","results":[]}')
        transactionRepository.save(_ as TerminalTransaction) >> { TerminalTransaction t -> t }

        when:
        bridgeClient.sendSettlement()

        then:
        thrown(TerminalBridgeException)
    }
}
