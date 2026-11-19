package com.agency.api.application.accounts;

public class AccountManagementException extends RuntimeException {
    public enum Kind { NOT_FOUND, CONFLICT, FORBIDDEN, INVALID }
    private final Kind kind;
    public AccountManagementException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
}
