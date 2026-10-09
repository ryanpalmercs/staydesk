package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;

@Table("remote_checkin_tokens")
public record RemoteCheckInToken(@Id int id, int reservationId, String tokenHash, LocalDateTime expiresAt,
                                 @Nullable LocalDateTime usedAt, LocalDateTime createdAt) {
}
