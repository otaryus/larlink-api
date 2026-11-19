package com.agency.api.application.accounts;

public class AuthWorkflowException extends RuntimeException {
    public enum Kind { INVALID, FORBIDDEN, CONFLICT, UNAUTHORIZED }
    private final Kind kind;
    public AuthWorkflowException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
}
