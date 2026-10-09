package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per nightly terminal settlement (batch-out) attempt. Stores only the
 * terminal-reported totals - deliberately not a copy of the folio-side total, so the
 * two can never drift. Reconciling this against {@code folio_payments} for the same
 * date is a query, not a stored duplicate.
 */
@Table("terminal_settlement_batches")
public record TerminalSettlementBatch(@Id int id, LocalDate batchDate, Status status, @Nullable String terminalId,
                                      @Nullable String merchantId, @Nullable Integer saleCount,
                                      @Nullable BigDecimal saleAmount, @Nullable Integer refundCount,
                                      @Nullable BigDecimal refundAmount, @Nullable Integer voidCount,
                                      @Nullable BigDecimal voidAmount, @Nullable String failureReason,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum Status {
        COMPLETED, FAILED
    }
}
