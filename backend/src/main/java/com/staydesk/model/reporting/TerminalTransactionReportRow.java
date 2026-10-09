package com.staydesk.model.reporting;

import com.staydesk.model.TerminalTransaction;

import java.math.BigDecimal;

public record TerminalTransactionReportRow(TerminalTransaction.Operation operation, TerminalTransaction.Status status,
                                           int transactionCount, BigDecimal totalAmount) {
}
