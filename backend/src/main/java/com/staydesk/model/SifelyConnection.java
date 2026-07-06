package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("sifely_connections")
public record SifelyConnection(@Id int id, EncryptedToken clientToken, String clientId, LocalDateTime connectedAt) {
}