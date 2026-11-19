package com.agency.api.adapters.persistence;

import com.agency.api.application.incidents.IncidentWorkflowPort;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class IncidentWorkflowPersistenceAdapter implements IncidentWorkflowPort {
    private final AccountRepository accounts;
    private final ContractRepository contracts;
    private final IncidentRepository incidents;
    private final IncidentMessageRepository messages;
    private final AuditService audit;

    public IncidentWorkflowPersistenceAdapter(AccountRepository accounts, ContractRepository contracts,
            IncidentRepository incidents, IncidentMessageRepository messages, AuditService audit) {
        this.accounts = accounts;
        this.contracts = contracts;
        this.incidents = incidents;
        this.messages = messages;
        this.audit = audit;
    }

    @Override public Optional<Account> findAccount(UUID id) { return accounts.findById(id); }
    @Override public Optional<EmploymentContract> findContract(UUID id) { return contracts.findById(id); }
    @Override public Optional<IncidentReport> findIncident(UUID id) { return incidents.findById(id); }
    @Override public Optional<IncidentMessage> findMessage(UUID id) { return messages.findById(id); }
    @Override public List<IncidentReport> findAllIncidents() { return incidents.findAllByOrderByCreatedAtDesc(); }
    @Override public List<IncidentMessage> messages(UUID incidentId) {
        return messages.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }
    @Override public IncidentReport saveIncident(IncidentReport incident) { return incidents.save(incident); }
    @Override public IncidentMessage saveMessage(IncidentMessage message) { return messages.save(message); }
    @Override public void record(Account actor, String action, String entityType, UUID entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }
    @Override public void notify(UUID accountId, String type, String message, String entityType, UUID entityId) {
        audit.notify(accountId, type, message, entityType, entityId);
    }
    @Override public void notifyRole(Role role, String type, String message, String entityType, UUID entityId) {
        audit.notifyRole(role, type, message, entityType, entityId);
    }
}
