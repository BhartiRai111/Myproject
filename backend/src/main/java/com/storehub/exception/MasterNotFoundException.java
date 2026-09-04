package com.storehub.exception;

public class MasterNotFoundException extends RuntimeException {
    public MasterNotFoundException(String entityName, Long id) {
        super(entityName + " not found with id: " + id);
    }
}
