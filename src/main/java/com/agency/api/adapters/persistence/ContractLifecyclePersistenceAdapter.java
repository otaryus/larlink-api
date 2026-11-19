package com.agency.api.adapters.persistence;

import com.agency.api.application.contracts.ContractLifecyclePort;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import com.agency.api.service.ContractDocumentService;
import com.agency.api.service.ServerDateService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Component
public class ContractLifecyclePersistenceAdapter implements ContractLifecyclePort {
    private final ContractRepository contracts;
    private final AccountRepository accounts;
    private final ContractDocumentService documents;
    private final AuditService audit;
    private final ServerDateService serverDate;

    public ContractLifecyclePersistenceAdapter(
            ContractRepository contracts,
            AccountRepository accounts,
            ContractDocumentService documents,
            AuditService audit,
            ServerDateService serverDate) {
        this.contracts = contracts;
        this.accounts = accounts;
        this.documents = documents;
        this.audit = audit;
        this.serverDate = serverDate;
    }

    @Override
    public Optional<EmploymentContract> findContract(UUID id) {
        return contracts.findById(id);
    }

    @Override
    public Optional<EmploymentContract> lockContract(UUID id) {
        return contracts.findLockedById(id);
    }

    @Override
    public Optional<Account> findAccount(UUID id) {
        return accounts.findById(id);
    }

    @Override
    public LocalDate currentDate() {
        return serverDate.currentDate();
    }

    @Override
    public boolean hasOtherActiveContract(UUID contractId, UUID housekeeperId) {
        return contracts.findAll().stream().anyMatch(other -> !other.getId().equals(contractId)
                && other.getHireRequest().getHousekeeper().getId().equals(housekeeperId)
                && "ACTIVE".equals(other.getStatus()));
    }

    @Override
    public ContractRevision addVersion(EmploymentContract contract, Account actor, String content, String reason, boolean addendum) {
        return documents.addVersion(contract, actor, content, reason, addendum);
    }

    @Override public List<ContractRevision> history(UUID contractId) { return documents.history(contractId); }
    @Override public void ensureInitialVersion(EmploymentContract contract) { documents.ensureInitialVersion(contract); }
    @Override public byte[] pdf(EmploymentContract contract) { return documents.pdf(contract); }

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
