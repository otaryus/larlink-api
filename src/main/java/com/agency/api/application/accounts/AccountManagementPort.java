package com.agency.api.application.accounts;

import com.agency.api.domain.*;
import java.util.Optional;
import java.util.UUID;

public interface AccountManagementPort {
    boolean emailExists(String email);
    Optional<Account> account(UUID id);
    Account save(Account account);
    void saveClient(ClientProfile profile);
    String temporaryPassword();
    String encode(String rawPassword);
    void record(Account actor, String action, String entityType, UUID entityId, String details);
}
