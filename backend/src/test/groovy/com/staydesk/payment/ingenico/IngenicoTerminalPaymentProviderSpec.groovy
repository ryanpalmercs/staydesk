package com.staydesk.payment.ingenico

import com.staydesk.exception.TerminalBridgeException
import com.staydesk.model.TerminalTransaction
import spock.lang.Specification

class IngenicoTerminalPaymentProviderSpec extends Specification {

    IngenicoBridgeClient bridgeClient = Mock()

    IngenicoTerminalPaymentProvider provider = new IngenicoTerminalPaymentProvider(bridgeClient)

    def "sale returns a successful AuthResult on approval"() {
        given:
        def result = new TsiTransactionResult("approved", "ref-1", "AUTH123", "APPROVED", new TsiCard("************2205"))
        bridgeClient.sendTransaction(TerminalTransaction.Operation.SALE, null, "sale", BigDecimal.valueOf(64.17), null) >> result

        when:
        def authResult = provider.sale(BigDecimal.valueOf(64.17), "unused-token", "Room charge", null, null)

        then:
        authResult.success()
        authResult.transactionId() == "ref-1"
        authResult.cardLast4() == "2205"
    }

    def "sale threads a known folio_payment_id through to the bridge client"() {
        given:
        def result = new TsiTransactionResult("approved", "ref-1", "AUTH123", "APPROVED", new TsiCard("************2205"))
        bridgeClient.sendTransaction(TerminalTransaction.Operation.SALE, 42, "sale", BigDecimal.valueOf(64.17), null) >> result

        when:
        def authResult = provider.sale(BigDecimal.valueOf(64.17), "unused-token", "Room charge", null, 42)

        then:
        authResult.success()
    }

    def "sale returns a failed AuthResult on decline"() {
        given:
        def result = new TsiTransactionResult("decline_by_host_or_card", null, null, "DECLINED", null)
        bridgeClient.sendTransaction(*_) >> result

        when:
        def authResult = provider.sale(BigDecimal.valueOf(64.17), "unused-token", "Room charge", null, null)

        then:
        !authResult.success()
        authResult.message() == "DECLINED"
    }

    def "sale returns a failed AuthResult when the bridge is offline"() {
        given:
        bridgeClient.sendTransaction(*_) >> { throw new TerminalBridgeException("Terminal bridge is not connected") }

        when:
        def authResult = provider.sale(BigDecimal.valueOf(64.17), "unused-token", "Room charge", null, null)

        then:
        !authResult.success()
        authResult.message() == "Terminal bridge is not connected"
    }

    def "void reports success using the terminal's returned reference_no"() {
        given:
        def result = new TsiTransactionResult("approved", "ref-2", "AUTH999", "APPROVED", null)
        bridgeClient.sendTransaction(TerminalTransaction.Operation.VOID, 9, "void", null, "ref-1") >> result

        when:
        def voidResult = provider.void_("ref-1", 9)

        then:
        voidResult.success()
        voidResult.transactionId() == "ref-2"
    }

    def "refund reports failure with the terminal's message on decline"() {
        given:
        def result = new TsiTransactionResult("decline_by_host_or_card", null, null, "REFUND DECLINED", null)
        bridgeClient.sendTransaction(TerminalTransaction.Operation.REFUND, 9, "refund", BigDecimal.valueOf(20), null) >> result

        when:
        def refundResult = provider.refund("tx-1", BigDecimal.valueOf(20), "2205", 9)

        then:
        !refundResult.success()
        refundResult.message() == "REFUND DECLINED"
    }

    def "authorize returns a successful AuthResult on approval"() {
        given:
        def result = new TsiTransactionResult("approved", "ref-3", "AUTH456", "APPROVED", new TsiCard("************2205"))
        bridgeClient.sendTransaction(TerminalTransaction.Operation.PRE_AUTH, null, "pre_auth", BigDecimal.valueOf(75), null) >> result

        when:
        def authResult = provider.authorize(BigDecimal.valueOf(75), "unused-token", "Incidentals hold", null, null)

        then:
        authResult.success()
        authResult.transactionId() == "ref-3"
        authResult.cardLast4() == "2205"
    }

    def "authorize returns a failed AuthResult when the bridge is offline"() {
        given:
        bridgeClient.sendTransaction(*_) >> { throw new TerminalBridgeException("Terminal bridge is not connected") }

        when:
        def authResult = provider.authorize(BigDecimal.valueOf(75), "unused-token", "Incidentals hold", null, null)

        then:
        !authResult.success()
        authResult.message() == "Terminal bridge is not connected"
    }

    def "capture reports success using the terminal's returned reference_no"() {
        given:
        def result = new TsiTransactionResult("approved", "ref-4", "AUTH789", "APPROVED", null)
        bridgeClient.sendTransaction(TerminalTransaction.Operation.PRE_AUTH_COMPLETION, 9, "pre_auth_completion",
                BigDecimal.valueOf(75), "ref-3") >> result

        when:
        def captureResult = provider.capture("ref-3", BigDecimal.valueOf(75), 9)

        then:
        captureResult.success()
        captureResult.transactionId() == "ref-4"
    }

    def "capture reports failure with the terminal's message on decline"() {
        given:
        def result = new TsiTransactionResult("decline_by_host_or_card", null, null, "DECLINED", null)
        bridgeClient.sendTransaction(*_) >> result

        when:
        def captureResult = provider.capture("ref-3", BigDecimal.valueOf(75), null)

        then:
        !captureResult.success()
        captureResult.message() == "DECLINED"
    }

    def "createReusableCredential is a pure passthrough"() {
        when:
        def result = provider.createReusableCredential("ref-1", "folio-1")

        then:
        0 * bridgeClient._
        result.success()
        result.providerToken() == "ref-1"
    }
}
