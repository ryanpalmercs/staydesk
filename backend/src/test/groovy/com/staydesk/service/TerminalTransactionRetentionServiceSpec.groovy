package com.staydesk.service

import com.staydesk.repository.TerminalTransactionRepository
import spock.lang.Specification

import java.time.LocalDateTime

class TerminalTransactionRetentionServiceSpec extends Specification {

    TerminalTransactionRepository terminalTransactionRepository = Mock()

    TerminalTransactionRetentionService service = new TerminalTransactionRetentionService(terminalTransactionRepository)

    def "purgeEligibleTransactions deletes rows older than the 3-year retention window"() {
        given:
        terminalTransactionRepository.deleteByCreatedAtBefore(_) >> 7

        when:
        def purged = service.purgeEligibleTransactions()

        then:
        1 * terminalTransactionRepository.deleteByCreatedAtBefore({ LocalDateTime cutoff ->
            cutoff.isBefore(LocalDateTime.now().minusYears(3).plusMinutes(1)) &&
                    cutoff.isAfter(LocalDateTime.now().minusYears(3).minusMinutes(1))
        }) >> 7
        purged == 7
    }

    def "purgeEligibleTransactions returns zero when nothing is eligible"() {
        given:
        terminalTransactionRepository.deleteByCreatedAtBefore(_) >> 0

        expect:
        service.purgeEligibleTransactions() == 0
    }
}
