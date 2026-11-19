package com.agency.api.application.incidents;

public class IncidentForbiddenException extends RuntimeException {
    public IncidentForbiddenException(String message) {
        super(message);
    }
}
