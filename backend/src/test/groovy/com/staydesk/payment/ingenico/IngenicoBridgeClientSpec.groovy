package com.staydesk.payment.ingenico

import com.fasterxml.jackson.databind.ObjectMapper
import com.staydesk.bridge.TerminalBridgeSessionRegistry
import com.staydesk.model.TerminalTransaction
import com.staydesk.repository.TerminalTransactionRepository
import spock.lang.Specification

class IngenicoBridgeClientSpec extends Specification {

    TerminalBridgeSessionRegistry sessionRegistry = Mock()
    TerminalTransactionRepository transactionRepository = Mock()
    ObjectMapper objectMapper = new ObjectMapper()

    IngenicoBridgeClient bridgeClient = new IngenicoBridgeClient(sessionRegistry, transactionRepository, objectMapper)

    private static TerminalTransaction pendingRow(int id, Integer folioPaymentId) {
        new TerminalTransaction(id, "flow-id-placeholder", folioPaymentId, TerminalTransaction.Operation.SALE,
                BigDecimal.valueOf(64.17), TerminalTransaction.Status.PENDING, null, null, null, null,
                "{}", null, null, null)
    }

    /**
     * Simulates the terminal responding by completing the flow synchronously from inside the
     * sessionRegistry.send(...) stub, so sendTransaction()'s future.get() returns immediately
     * instead of the test needing a real second thread.
     */
    private void respondWith(Map resultFields) {
        sessionRegistry.send(_) >> { String json ->
            def parsed = objectMapper.readTree(json)
            String flowId = parsed.get("request").get("flow_id").asText()
            def eventResource = objectMapper.createObjectNode()
            def results = eventResource.putArray("results")
            def resultNode = objectMapper.valueToTree(resultFields)
            results.add(resultNode)
            bridgeClient.completeFlow(flowId, eventResource)
        }
    }

    def "sendTransaction promotes reference_no, authorization_no, card_last4 and host_response_text onto the saved row"() {
        given:
        respondWith(status: "approved", reference_no: "ref-1", authorization_no: "AUTH123",
                host_response_text: "APPROVED", card: [account_no: "************2205"])

        when:
        def result = bridgeClient.sendTransaction(TerminalTransaction.Operation.SALE, null, "sale", BigDecimal.valueOf(64.17), null)

        then:
        result.status() == "approved"
        1 * transactionRepository.save({ TerminalTransaction tt -> tt.status() == TerminalTransaction.Status.PENDING }) >> pendingRow(1, null)
        1 * transactionRepository.save({ TerminalTransaction tt ->
            tt.status() == TerminalTransaction.Status.COMPLETED &&
                    tt.referenceNo() == "ref-1" &&
                    tt.authorizationNo() == "AUTH123" &&
                    tt.cardLast4() == "2205" &&
                    tt.hostResponseText() == "APPROVED"
        })
    }

    def "sendTransaction marks the row FAILED without structured fields when the terminal declines"() {
        given:
        respondWith(status: "decline_by_host_or_card", reference_no: null, authorization_no: null,
                host_response_text: "DECLINED")

        when:
        def result = bridgeClient.sendTransaction(TerminalTransaction.Operation.SALE, null, "sale", BigDecimal.valueOf(64.17), null)

        then:
        result.status() == "decline_by_host_or_card"
        1 * transactionRepository.save({ TerminalTransaction tt -> tt.status() == TerminalTransaction.Status.PENDING }) >> pendingRow(1, null)
        1 * transactionRepository.save({ TerminalTransaction tt ->
            tt.status() == TerminalTransaction.Status.FAILED && tt.hostResponseText() == "DECLINED"
        })
    }
}
