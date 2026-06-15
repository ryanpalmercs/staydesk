package com.staydesk.folio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "folios")
public class Folio {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FolioStatus status = FolioStatus.OPEN;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_amount_cents")
    private Long paidAmountCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Folio() {
    }

    public Folio(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public FolioStatus getStatus() {
        return status;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Long getPaidAmountCents() {
        return paidAmountCents;
    }

    public boolean isPaid() {
        return status == FolioStatus.PAID;
    }

    public void markPaid(String paymentIntentId, long amountReceivedCents, Instant paidAt) {
        this.status = FolioStatus.PAID;
        this.stripePaymentIntentId = paymentIntentId;
        this.paidAmountCents = amountReceivedCents;
        this.paidAt = paidAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
