package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("terminal_transactions")
public record TerminalTransaction(@Id int id, String flowId, Integer folioPaymentId, Operation operation,
                                  BigDecimal amount, Status status, String requestPayload, String responsePayload,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum Operation {
        SALE, VOID, REFUND, PRE_AUTH, PRE_AUTH_COMPLETION
    }

    public enum Status {
        PENDING, COMPLETED, FAILED, UNKNOWN
    }
}
