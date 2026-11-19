package com.agency.api.application.finance;

public class FinanceForbiddenException extends RuntimeException {
    public FinanceForbiddenException(String message) { super(message); }
}
