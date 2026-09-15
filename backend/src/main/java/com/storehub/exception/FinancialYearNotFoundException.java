package com.storehub.exception;

public class FinancialYearNotFoundException extends RuntimeException {
    public FinancialYearNotFoundException(Long id) {
        super("Financial year not found with id: " + id);
    }
}
