package com.staydesk.model.request;

public record CreateInventoryItemRequest(String name, String category, String unit, int lowStockThreshold) {
}
