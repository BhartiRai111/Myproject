package com.storehub.exception;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(Long id) {
        super("Inventory record not found with id: " + id);
    }
}
