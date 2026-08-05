package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("folio_payments")
public record FolioPayment(@Id int id, int folioId, PaymentKind kind, String provider, String stripePaymentIntentId,
                           String cardLast4, PaymentStatus status, BigDecimal authorizedAmount,
                           BigDecimal capturedAmount, String failureReason, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum PaymentKind {
        ROOM, INCIDENTALS, INCIDENT_CHARGE
    }

    public enum PaymentStatus {
        REQUIRES_CAPTURE, CAPTURED, CANCELED, FAILED, PARTIALLY_REFUNDED
    }
}