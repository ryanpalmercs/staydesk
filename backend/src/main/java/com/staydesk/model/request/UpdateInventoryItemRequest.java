package com.staydesk.model.request;

public record UpdateInventoryItemRequest(String name, String category, String unit, int lowStockThreshold,
                                         boolean active) {
}
