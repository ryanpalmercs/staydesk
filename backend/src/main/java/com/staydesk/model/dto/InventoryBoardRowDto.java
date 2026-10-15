package com.staydesk.model.dto;

public record InventoryBoardRowDto(int itemId, String name, String category, String unit, int currentStock,
                                   int lowStockThreshold, boolean lowStock) {
}
