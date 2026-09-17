package com.storehub.exception;

public class StockTransferNotFoundException extends RuntimeException {
    public StockTransferNotFoundException(Long id) {
        super("Stock transfer not found with id: " + id);
    }
}
