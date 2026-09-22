package com.staydesk.service

import com.staydesk.exception.TerminalBridgeException
import com.staydesk.model.TerminalSettlementBatch
import com.staydesk.payment.ingenico.IngenicoBridgeClient
import com.staydesk.payment.ingenico.TsiSettlementResult
import com.staydesk.payment.ingenico.TsiTerminalTotal
import com.staydesk.repository.TerminalSettlementBatchRepository
import spock.lang.Specification

class TerminalSettlementServiceSpec extends Specification {

    IngenicoBridgeClient bridgeClient = Mock()
    TerminalSettlementBatchRepository settlementBatchRepository = Mock()

    TerminalSettlementService settlementService = new TerminalSettlementService(bridgeClient, settlementBatchRepository)

    def "stores a COMPLETED batch row with the terminal's reported totals"() {
        given:
        def total = new TsiTerminalTotal(12, 123456L, 1, 2000L, 0, 0L)
        bridgeClient.sendSettlement() >> new TsiSettlementResult("approved", "001", "VISAMID0123", "APPROVED", total)

        when:
        settlementService.runNightlySettlement()

        then:
        1 * settlementBatchRepository.save({ TerminalSettlementBatch batch ->
            batch.status() == TerminalSettlementBatch.Status.COMPLETED &&
            batch.terminalId() == "001" &&
            batch.merchantId() == "VISAMID0123" &&
            batch.saleCount() == 12 &&
            batch.saleAmount() == new BigDecimal("1234.56") &&
            batch.refundCount() == 1 &&
            batch.refundAmount() == new BigDecimal("20.00") &&
            batch.voidCount() == 0 &&
            batch.voidAmount() == new BigDecimal("0.00") &&
            batch.failureReason() == null
        }) >> { TerminalSettlementBatch b -> b }
    }

    def "stores a COMPLETED batch row with null totals when the terminal omits terminal_total"() {
        given:
        bridgeClient.sendSettlement() >> new TsiSettlementResult("approved", "001", "VISAMID0123", "APPROVED", null)

        when:
        settlementService.runNightlySettlement()

        then:
        1 * settlementBatchRepository.save({ TerminalSettlementBatch batch ->
            batch.status() == TerminalSettlementBatch.Status.COMPLETED &&
            batch.saleCount() == null &&
            batch.saleAmount() == null
        }) >> { TerminalSettlementBatch b -> b }
    }

    def "stores a FAILED batch row and does not throw or retry when the bridge is offline"() {
        given:
        bridgeClient.sendSettlement() >> { throw new TerminalBridgeException("Terminal bridge is not connected") }

        when:
        settlementService.runNightlySettlement()

        then:
        noExceptionThrown()
        1 * settlementBatchRepository.save({ TerminalSettlementBatch batch ->
            batch.status() == TerminalSettlementBatch.Status.FAILED &&
            batch.failureReason() == "Terminal bridge is not connected" &&
            batch.terminalId() == null &&
            batch.saleAmount() == null
        }) >> { TerminalSettlementBatch b -> b }
    }
}
