package com.agency.api.application.contracts;

public class ContractConflictException extends RuntimeException {
    public ContractConflictException(String message) {
        super(message);
    }
}
