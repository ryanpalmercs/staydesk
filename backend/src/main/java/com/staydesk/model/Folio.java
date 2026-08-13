package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("folios")
public record Folio(@Id int id, FolioStatus status, BigDecimal total,
                    LocalDateTime paidAt, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum FolioStatus {
        OPEN, CLOSED
    }
}