package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("quickbooks_connections")
public record QuickBooksConnection(@Id int id, String realmId, EncryptedToken refreshToken,
                                   LocalDateTime refreshTokenExpiresAt, LocalDateTime connectedAt) {
}
