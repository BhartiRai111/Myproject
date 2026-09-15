package com.storehub.exception;

public class CashTransactionNotFoundException extends RuntimeException {
    public CashTransactionNotFoundException(Long id) {
        super("Cash transaction not found with id: " + id);
    }
}
