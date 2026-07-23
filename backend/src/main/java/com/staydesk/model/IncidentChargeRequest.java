  package com.staydesk.model;

  import org.springframework.data.annotation.Id;
  import org.springframework.data.relational.core.mapping.Table;

  import java.math.BigDecimal;
  import java.time.LocalDateTime;
  import java.util.UUID;

  @Table("incident_charge_requests")
  public record IncidentChargeRequest(@Id int id, int folioId, int reusablePaymentCredentialId, BigDecimal amount,
                                      String reason, IncidentChargeStatus status, UUID requestedBy,
                                      LocalDateTime requestedAt, UUID approvedBy, LocalDateTime approvedAt,
                                      String rejectionReason, Integer folioPaymentId, String failureReason,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {

      public enum IncidentChargeStatus {
          PENDING, APPROVED, REJECTED, CHARGED, FAILED
      }
  }