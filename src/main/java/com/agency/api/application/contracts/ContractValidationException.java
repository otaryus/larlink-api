package com.agency.api.application.contracts;

public class ContractValidationException extends RuntimeException {
    public ContractValidationException(String message) {
        super(message);
    }
}
