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
        def authResult = provider.sale(BigDecimal.valueOf(64.17), "unused-token", "Room charge")

        then:
        authResult.success()
        authResult.transactionId() == "ref-1"
        authResult.cardLast4() == "2205"
    }

    def "sale returns a failed AuthResult on decline"() {
        given:
        def result = new TsiTransactionResult("decline_by_host_or_card", null, null, "DECLINED", null)
        bridgeClient.sendTransaction(*_) >> result

        when:
        def authResult = provider.sale(BigDecimal.valueOf(64.17), "unused-token", "Room charge")

        then:
        !authResult.success()
        authResult.message() == "DECLINED"
    }

    def "sale returns a failed AuthResult when the bridge is offline"() {
        given:
        bridgeClient.sendTransaction(*_) >> { throw new TerminalBridgeException("Terminal bridge is not connected") }

        when:
        def authResult = provider.sale(BigDecimal.valueOf(64.17), "unused-token", "Room charge")

        then:
        !authResult.success()
        authResult.message() == "Terminal bridge is not connected"
    }

    def "void reports success using the terminal's returned reference_no"() {
        given:
        def result = new TsiTransactionResult("approved", "ref-2", "AUTH999", "APPROVED", null)
        bridgeClient.sendTransaction(TerminalTransaction.Operation.VOID, null, "void", null, "ref-1") >> result

        when:
        def voidResult = provider.void_("ref-1")

        then:
        voidResult.success()
        voidResult.transactionId() == "ref-2"
    }

    def "refund reports failure with the terminal's message on decline"() {
        given:
        def result = new TsiTransactionResult("decline_by_host_or_card", null, null, "REFUND DECLINED", null)
        bridgeClient.sendTransaction(TerminalTransaction.Operation.REFUND, null, "refund", BigDecimal.valueOf(20), null) >> result

        when:
        def refundResult = provider.refund("tx-1", BigDecimal.valueOf(20), "2205")

        then:
        !refundResult.success()
        refundResult.message() == "REFUND DECLINED"
    }

    def "authorize is not yet implemented pending Elavon/Ingenico confirmation"() {
        when:
        provider.authorize(BigDecimal.valueOf(75), "unused-token", "Incidentals hold")

        then:
        thrown(UnsupportedOperationException)
    }

    def "capture is not yet implemented pending Elavon/Ingenico confirmation"() {
        when:
        provider.capture("ref-1", BigDecimal.valueOf(75))

        then:
        thrown(UnsupportedOperationException)
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
