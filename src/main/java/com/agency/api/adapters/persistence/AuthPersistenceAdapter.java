package com.agency.api.adapters.persistence;

import com.agency.api.application.accounts.AuthPort;
import com.agency.api.domain.*;
import com.agency.api.repository.AccountRepository;
import com.agency.api.security.JwtService;
import com.agency.api.service.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
public class AuthPersistenceAdapter implements AuthPort {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    public AuthPersistenceAdapter(AccountRepository accounts, PasswordEncoder encoder, JwtService jwt,
            AuditService audit) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }
    @Override public Optional<Account> byEmail(String email) { return accounts.findByEmailIgnoreCase(email); }
    @Override public Account save(Account account) { return accounts.save(account); }
    @Override public String encode(String password) { return encoder.encode(password); }
    @Override public boolean matches(String password, String encoded) { return encoder.matches(password, encoded); }
    @Override public String token(Account account) { return jwt.issue(account); }
    @Override public void record(Account actor, String action, String type, UUID id, String details) {
        audit.record(actor, action, type, id, details);
    }
    @Override public void notifyRole(Role role, String type, String message, String entityType, UUID entityId) {
        audit.notifyRole(role, type, message, entityType, entityId);
    }
}
