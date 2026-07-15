package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("stripe_connections")
public record StripeConnection(@Id int id, String stripeAccountId, LocalDateTime connectedAt) {
}
