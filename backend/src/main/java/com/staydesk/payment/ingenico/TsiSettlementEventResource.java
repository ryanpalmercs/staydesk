package com.staydesk.payment.ingenico;

import java.util.List;

/**
 * Mirrors {@link TsiEventResource}'s shape (top-level status + a results list) but
 * typed for a settlement event, whose results carry batch totals rather than a
 * per-transaction outcome.
 */
public record TsiSettlementEventResource(String status, List<TsiSettlementResult> results) {
}
