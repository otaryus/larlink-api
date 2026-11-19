package com.agency.api.adapters.persistence;

import com.agency.api.application.absence.AbsenceWorkflowPort;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AbsenceWorkflowPersistenceAdapter implements AbsenceWorkflowPort {
    private final AccountRepository accounts;
    private final ContractRepository contracts;
    private final AbsenceRepository absences;
    private final AuditService audit;

    public AbsenceWorkflowPersistenceAdapter(
            AccountRepository accounts,
            ContractRepository contracts,
            AbsenceRepository absences,
            AuditService audit) {
        this.accounts = accounts;
        this.contracts = contracts;
        this.absences = absences;
        this.audit = audit;
    }

    @Override
    public Optional<Account> findAccount(UUID id) {
        return accounts.findById(id);
    }

    @Override
    public Optional<EmploymentContract> findContract(UUID id) {
        return contracts.findById(id);
    }

    @Override
    public Optional<AbsenceRecord> findAbsence(UUID id) {
        return absences.findById(id);
    }

    @Override
    public List<AbsenceRecord> findAll() {
        return absences.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public AbsenceRecord save(AbsenceRecord absence) {
        return absences.save(absence);
    }

    @Override
    public void record(Account actor, String action, String entityType, UUID entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }

    @Override
    public void notify(UUID accountId, String type, String message, String entityType, UUID entityId) {
        audit.notify(accountId, type, message, entityType, entityId);
    }

    @Override
    public void notifyRole(Role role, String type, String message, String entityType, UUID entityId) {
        audit.notifyRole(role, type, message, entityType, entityId);
    }
}
