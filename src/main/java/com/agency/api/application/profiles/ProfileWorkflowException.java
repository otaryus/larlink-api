package com.agency.api.application.profiles;

public class ProfileWorkflowException extends RuntimeException {
    public enum Kind { NOT_FOUND, CONFLICT, FORBIDDEN, INVALID }
    private final Kind kind;
    public ProfileWorkflowException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
}
