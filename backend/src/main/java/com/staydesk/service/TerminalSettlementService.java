package com.staydesk.service;

import com.staydesk.exception.TerminalBridgeException;
import com.staydesk.model.TerminalSettlementBatch;
import com.staydesk.payment.ingenico.IngenicoAmountFormat;
import com.staydesk.payment.ingenico.IngenicoBridgeClient;
import com.staydesk.payment.ingenico.TsiSettlementResult;
import com.staydesk.payment.ingenico.TsiTerminalTotal;
import com.staydesk.repository.TerminalSettlementBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Triggers the Desk 3500 terminal's nightly settlement (batch-out). Staydesk has to
 * drive this itself - a 2026-08-03/04 Banccard/Elavon call confirmed there is no
 * terminal-side auto-settlement schedule to rely on.
 * <p>
 * Gated behind {@code terminal.settlement.enabled} (env {@code TERMINAL_SETTLEMENT_ENABLED}),
 * defaulting to disabled - this is new, unverified against real Desk 3500 hardware, and an
 * unattended nightly job is not something to risk running unattended in production before
 * that verification happens. Mirrors the same infra-readiness gate pattern as
 * {@code payment.card-present.record-only} in {@link com.staydesk.provider.ProviderFactory}.
 * <p>
 * Failure handling: if the run fails (bridge offline, terminal timeout, unparseable
 * response, etc.) we log clearly and store a FAILED row so it's visible, but do not
 * retry automatically. A batch running a day late isn't urgent enough to justify retry
 * machinery, and a silent retry loop risks masking a bridge that's been down for days -
 * a human noticing and manually triggering settlement (or fixing the bridge) is the
 * intended recovery path.
 */
@Service
public class TerminalSettlementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSettlementService.class);

    private final IngenicoBridgeClient bridgeClient;
    private final TerminalSettlementBatchRepository settlementBatchRepository;
    private final boolean settlementEnabled;

    public TerminalSettlementService(IngenicoBridgeClient bridgeClient,
                                     TerminalSettlementBatchRepository settlementBatchRepository,
                                     @Value("${terminal.settlement.enabled:false}") boolean settlementEnabled) {
        this.bridgeClient = bridgeClient;
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementEnabled = settlementEnabled;
    }

    @Scheduled(cron = "0 59 23 * * *", zone = "America/Chicago")
    public void runNightlySettlement() {
        if (!settlementEnabled) {
            LOGGER.debug("Nightly terminal settlement is disabled (terminal.settlement.enabled=false); skipping");
            return;
        }

        LocalDate batchDate = LocalDate.now();

        try {
            TsiSettlementResult result = bridgeClient.sendSettlement();
            saveCompleted(batchDate, result);
            LOGGER.info("Nightly terminal settlement completed for {}", batchDate);
        } catch (TerminalBridgeException e) {
            saveFailed(batchDate, e.getMessage());
            LOGGER.error("Nightly terminal settlement failed for {} - no automatic retry; reconcile manually " +
                         "against the terminal's own batch report and trigger settlement by hand once the bridge " +
                         "is back: {}", batchDate, e.getMessage());
        }
    }

    private void saveCompleted(LocalDate batchDate, TsiSettlementResult result) {
        TsiTerminalTotal total = result.terminalTotal();

        settlementBatchRepository.save(new TerminalSettlementBatch(0, batchDate, TerminalSettlementBatch.Status.COMPLETED,
                result.terminalId(), result.merchantId(),
                total != null ? total.saleCount() : null, total != null ? IngenicoAmountFormat.fromCents(total.saleAmountCents()) : null,
                total != null ? total.refundCount() : null, total != null ? IngenicoAmountFormat.fromCents(total.refundAmountCents()) : null,
                total != null ? total.voidCount() : null, total != null ? IngenicoAmountFormat.fromCents(total.voidAmountCents()) : null,
                null, LocalDateTime.now(), null));
    }

    private void saveFailed(LocalDate batchDate, String reason) {
        settlementBatchRepository.save(new TerminalSettlementBatch(0, batchDate, TerminalSettlementBatch.Status.FAILED,
                null, null, null, null, null, null, null, null, reason, LocalDateTime.now(), null));
    }
}
