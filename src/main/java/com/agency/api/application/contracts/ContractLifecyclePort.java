package com.agency.api.application.contracts;

import com.agency.api.domain.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ContractLifecyclePort {
    Optional<EmploymentContract> findContract(UUID id);

    Optional<EmploymentContract> lockContract(UUID id);

    Optional<Account> findAccount(UUID id);

    LocalDate currentDate();

    boolean hasOtherActiveContract(UUID contractId, UUID housekeeperId);

    ContractRevision addVersion(EmploymentContract contract, Account actor, String content, String reason, boolean addendum);

    List<ContractRevision> history(UUID contractId);

    void ensureInitialVersion(EmploymentContract contract);

    byte[] pdf(EmploymentContract contract);

    void record(Account actor, String action, String entityType, UUID entityId, String details);

    void notify(UUID accountId, String type, String message, String entityType, UUID entityId);

    void notifyRole(Role role, String type, String message, String entityType, UUID entityId);
}
