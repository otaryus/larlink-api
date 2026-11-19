package com.agency.api.application.accounts;

import com.agency.api.domain.Account;
import com.agency.api.domain.Role;
import java.util.Optional;
import java.util.UUID;

public interface AuthPort {
    Optional<Account> byEmail(String email);
    Account save(Account account);
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
    String token(Account account);
    void record(Account actor, String action, String entityType, UUID entityId, String details);
    void notifyRole(Role role, String type, String message, String entityType, UUID entityId);
}
