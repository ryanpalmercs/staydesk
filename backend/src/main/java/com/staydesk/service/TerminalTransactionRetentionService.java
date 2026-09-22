package com.staydesk.service;

import com.staydesk.repository.TerminalTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Period;

/**
 * Mirrors {@link RetentionService}'s guest PII retention job, but for terminal_transactions:
 * card-present terminal request/response payloads (and the card_last4/authorization_no/etc.
 * columns promoted alongside them) are hard-deleted past the same 3-year retention window
 * rather than anonymized, since there's no ongoing guest record to preserve once purged.
 */
@Service
public class TerminalTransactionRetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalTransactionRetentionService.class);
    private static final Period RETENTION_PERIOD = Period.ofYears(3);

    private final TerminalTransactionRepository terminalTransactionRepository;

    public TerminalTransactionRetentionService(TerminalTransactionRepository terminalTransactionRepository) {
        this.terminalTransactionRepository = terminalTransactionRepository;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "America/Chicago")
    public void runPurgeJob() {
        int purged = purgeEligibleTransactions();
        LOGGER.info("Terminal transaction purge job completed. {} transaction(s) purged.", purged);
    }

    @Transactional
    public int purgeEligibleTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minus(RETENTION_PERIOD);
        return terminalTransactionRepository.deleteByCreatedAtBefore(cutoff);
    }
}
