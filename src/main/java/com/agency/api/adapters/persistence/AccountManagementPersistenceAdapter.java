package com.agency.api.adapters.persistence;

import com.agency.api.application.accounts.AccountManagementPort;
import com.agency.api.domain.*;
import com.agency.api.repository.AccountRepository;
import com.agency.api.repository.ClientRepository;
import com.agency.api.service.AuditService;
import com.agency.api.service.TemporaryPasswordService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
public class AccountManagementPersistenceAdapter implements AccountManagementPort {
    private final AccountRepository accounts;
    private final ClientRepository clients;
    private final TemporaryPasswordService temporaryPasswords;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    public AccountManagementPersistenceAdapter(AccountRepository accounts, ClientRepository clients,
            TemporaryPasswordService temporaryPasswords, PasswordEncoder encoder, AuditService audit) {
        this.accounts = accounts;
        this.clients = clients;
        this.temporaryPasswords = temporaryPasswords;
        this.encoder = encoder;
        this.audit = audit;
    }
    @Override public boolean emailExists(String email) { return accounts.findByEmailIgnoreCase(email).isPresent(); }
    @Override public Optional<Account> account(UUID id) { return accounts.findById(id); }
    @Override public Account save(Account account) { return accounts.save(account); }
    @Override public void saveClient(ClientProfile profile) { clients.save(profile); }
    @Override public String temporaryPassword() { return temporaryPasswords.generate(); }
    @Override public String encode(String rawPassword) { return encoder.encode(rawPassword); }
    @Override public void record(Account actor, String action, String entityType, UUID entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }
}
