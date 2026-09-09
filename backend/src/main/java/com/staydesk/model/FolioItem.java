package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("folio_items")
public record FolioItem(@Id int id, int folioId, String description, BigDecimal amount, FolioItemType type,
                        @Nullable Integer extraId, @Nullable Integer quantity,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {

    public enum FolioItemType {
        CHARGE, TAX, PAYMENT
    }
}
