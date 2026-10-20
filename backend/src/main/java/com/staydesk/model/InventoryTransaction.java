package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("inventory_transactions")
public record InventoryTransaction(@Id int id, int itemId, Type type, int quantity, @Nullable Integer roomId,
                                   @Nullable UUID performedBy, @Nullable String note, LocalDateTime createdAt) {

    public enum Type {
        RESTOCK, USAGE
    }
}
