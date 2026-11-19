package com.agency.api.application.management;

public class ManagementForbiddenException extends RuntimeException {
    public ManagementForbiddenException(String message) { super(message); }
}
