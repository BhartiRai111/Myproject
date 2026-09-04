package com.storehub.exception;

public class SalesOrderNotFoundException extends RuntimeException {
    public SalesOrderNotFoundException(Long id) {
        super("Sales order not found with id: " + id);
    }
}
