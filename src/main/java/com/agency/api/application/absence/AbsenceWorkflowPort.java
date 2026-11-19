package com.agency.api.application.absence;

import com.agency.api.domain.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbsenceWorkflowPort {
    Optional<Account> findAccount(UUID id);

    Optional<EmploymentContract> findContract(UUID id);

    Optional<AbsenceRecord> findAbsence(UUID id);

    List<AbsenceRecord> findAll();

    AbsenceRecord save(AbsenceRecord absence);

    void record(Account actor, String action, String entityType, UUID entityId, String details);

    void notify(UUID accountId, String type, String message, String entityType, UUID entityId);

    void notifyRole(Role role, String type, String message, String entityType, UUID entityId);
}
