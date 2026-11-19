package com.agency.api.application.contracts;

public class ContractForbiddenException extends RuntimeException {
    public ContractForbiddenException(String message) {
        super(message);
    }
}
