package com.agency.api.application.absence;

public class AbsenceForbiddenException extends RuntimeException {
    public AbsenceForbiddenException(String message) {
        super(message);
    }
}
