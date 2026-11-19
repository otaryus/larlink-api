package com.agency.api.application.incidents;

public class IncidentValidationException extends RuntimeException {
    public IncidentValidationException(String message) {
        super(message);
    }
}
