  package com.staydesk.model;

  import org.springframework.data.annotation.Id;
  import org.springframework.data.relational.core.mapping.Table;

  import java.time.LocalDateTime;

  @Table("reusable_payment_credentials")
  public record ReusablePaymentCredential(@Id int id, int folioId, int reservationId, String provider,
                                          String providerCustomerId, String providerToken, String cardLast4,
                                          boolean revoked, LocalDateTime revokedAt, LocalDateTime expiresAt,
                                          LocalDateTime createdAt, LocalDateTime updatedAt) {
  }