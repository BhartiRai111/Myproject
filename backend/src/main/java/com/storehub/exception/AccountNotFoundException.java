package com.storehub.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Account not found with id: " + id);
    }

    public AccountNotFoundException(String accountCode) {
        super("Account not found with code: " + accountCode);
    }
}
