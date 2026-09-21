package com.staydesk.service;

import com.staydesk.model.TerminalTransaction;
import com.staydesk.repository.TerminalTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TerminalTransactionReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalTransactionReconciliationService.class);
    private static final int STALE_AFTER_MINUTES = 2;

    private final TerminalTransactionRepository terminalTransactionRepository;

    public TerminalTransactionReconciliationService(TerminalTransactionRepository terminalTransactionRepository) {
        this.terminalTransactionRepository = terminalTransactionRepository;
    }

    @Scheduled(fixedDelay = 60 * 1000)
    public void flagStalePendingTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES);
        List<TerminalTransaction> staleTransactions = terminalTransactionRepository.findByStatusAndCreatedAtBefore(TerminalTransaction.Status.PENDING, threshold);

        staleTransactions.forEach(this::flagUnknown);
    }

    private void flagUnknown(TerminalTransaction pendingTransaction) {
        terminalTransactionRepository.save(new TerminalTransaction(pendingTransaction.id(), pendingTransaction.flowId(),
                pendingTransaction.folioPaymentId(), pendingTransaction.operation(), pendingTransaction.amount(),
                TerminalTransaction.Status.UNKNOWN, pendingTransaction.requestPayload(), pendingTransaction.responsePayload(),
                pendingTransaction.createdAt(), null));

        LOGGER.error("Terminal transaction flow {} ({}) has an unknown outcome after {} minutes - reconcile manually " +
                     "against the terminal receipt/batch report before retrying", pendingTransaction.flowId(),
                pendingTransaction.operation(), STALE_AFTER_MINUTES);
    }
}
