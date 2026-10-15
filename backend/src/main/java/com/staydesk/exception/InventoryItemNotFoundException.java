package com.staydesk.exception;

public class InventoryItemNotFoundException extends RuntimeException {
    public InventoryItemNotFoundException() {
        super("Inventory item not found");
    }
}
