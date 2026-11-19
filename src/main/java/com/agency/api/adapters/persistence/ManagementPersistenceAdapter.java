package com.agency.api.adapters.persistence;

import com.agency.api.application.management.ManagementPort;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import com.agency.api.service.ServerDateService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ManagementPersistenceAdapter implements ManagementPort {
    private final AgencyTaskRepository tasks;
    private final AgencyFinancePolicyRepository policies;
    private final ContractRepository contracts;
    private final HireRequestRepository requests;
    private final PaymentObligationRepository payments;
    private final IncidentRepository incidents;
    private final AbsenceRepository absences;
    private final AdditionalWorkRepository additionalWork;
    private final UserNotificationRepository notifications;
    private final AuditEventRepository audits;
    private final ServerDateService serverDate;
    private final AuditService audit;

    public ManagementPersistenceAdapter(AgencyTaskRepository tasks, AgencyFinancePolicyRepository policies,
            ContractRepository contracts, HireRequestRepository requests, PaymentObligationRepository payments,
            IncidentRepository incidents, AbsenceRepository absences, AdditionalWorkRepository additionalWork,
            UserNotificationRepository notifications, AuditEventRepository audits, ServerDateService serverDate,
            AuditService audit) {
        this.tasks = tasks;
        this.policies = policies;
        this.contracts = contracts;
        this.requests = requests;
        this.payments = payments;
        this.incidents = incidents;
        this.absences = absences;
        this.additionalWork = additionalWork;
        this.notifications = notifications;
        this.audits = audits;
        this.serverDate = serverDate;
        this.audit = audit;
    }

    @Override public List<AgencyTask> activeTasks() { return tasks.findByActiveTrueOrderByNameAsc(); }
    @Override public boolean taskExistsByName(String name) { return tasks.existsByNameIgnoreCase(name); }
    @Override public Optional<AgencyTask> task(UUID id) { return tasks.findById(id); }
    @Override public AgencyTask saveTask(AgencyTask task) { return tasks.save(task); }
    @Override public Optional<AgencyFinancePolicy> financePolicy() { return policies.findById("agency"); }
    @Override public AgencyFinancePolicy saveFinancePolicy(AgencyFinancePolicy policy) { return policies.save(policy); }
    @Override public LocalDate serverDate() { return serverDate.currentDate(); }
    @Override public boolean testDateEnabled() { return serverDate.isTestDateEnabled(); }
    @Override public void record(Account actor, String action, String entityType, String entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }
    @Override public List<EmploymentContract> contracts() { return contracts.findAllByOrderByCreatedAtDesc(); }
    @Override public List<HireRequest> requests() { return requests.findAllByOrderByCreatedAtDesc(); }
    @Override public List<PaymentObligation> payments() { return payments.findAll(); }
    @Override public List<IncidentReport> incidents() { return incidents.findAll(); }
    @Override public List<AbsenceRecord> absences() { return absences.findAll(); }
    @Override public List<AdditionalWork> additionalWork() { return additionalWork.findAll(); }
    @Override public List<UserNotification> notifications(UUID accountId) {
        return notifications.findByAccountIdOrderByCreatedAtDesc(accountId);
    }
    @Override public Optional<UserNotification> notification(UUID id) { return notifications.findById(id); }
    @Override public List<AuditEvent> auditEvents() { return audits.findTop500ByOrderByCreatedAtDesc(); }
}
