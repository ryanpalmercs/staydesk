package com.staydesk.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("inventory_items")
public record InventoryItem(@Id int id, String name, String category, String unit, int lowStockThreshold,
                            boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
