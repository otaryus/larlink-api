package com.agency.api.application.requests;

public class RequestForbiddenException extends RuntimeException {
    public RequestForbiddenException(String message) {
        super(message);
    }
}
