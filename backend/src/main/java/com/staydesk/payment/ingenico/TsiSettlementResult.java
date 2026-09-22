package com.staydesk.payment.ingenico;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response shape for a settlement (batch-out) transaction (TSI spec §5.3.12).
 * <p>
 * Deliberately separate from {@link TsiTransactionResult}: a settlement reports
 * aggregate batch totals for the terminal/merchant, not a single card transaction's
 * outcome. Reusing {@code TsiTransactionResult} here would silently drop the
 * {@link TsiTerminalTotal} the batch-out job needs.
 */
public record TsiSettlementResult(String status,
                                  @JsonProperty("terminal_id") String terminalId,
                                  @JsonProperty("merchant_id") String merchantId,
                                  @JsonProperty("host_response_text") String hostResponseText,
                                  @JsonProperty("terminal_total") TsiTerminalTotal terminalTotal) {
}
