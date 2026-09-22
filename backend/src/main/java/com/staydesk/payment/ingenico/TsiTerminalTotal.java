package com.staydesk.payment.ingenico;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Batch totals reported by the terminal as part of a settlement (TSI spec §5.3.12).
 * Amounts are in cents, matching the request-side convention used elsewhere in this
 * package (see {@link IngenicoAmountFormat}).
 */
public record TsiTerminalTotal(@JsonProperty("sale_count") Integer saleCount,
                               @JsonProperty("sale_amount") Long saleAmountCents,
                               @JsonProperty("refund_count") Integer refundCount,
                               @JsonProperty("refund_amount") Long refundAmountCents,
                               @JsonProperty("void_count") Integer voidCount,
                               @JsonProperty("void_amount") Long voidAmountCents) {
}
