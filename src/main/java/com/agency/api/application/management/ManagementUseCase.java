package com.agency.api.application.management;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ManagementUseCase {
    private static final Set<String> SKILLS = Set.of("CLEANING", "COOKING", "CHILDCARE", "ELDERLY_CARE",
            "LAUNDRY", "GARDENING", "OTHER");
    private final ManagementPort management;

    public ManagementUseCase(ManagementPort management) { this.management = management; }

    @Transactional(readOnly = true)
    public List<AgencyTask> activeTasks() { return management.activeTasks(); }

    @Transactional(noRollbackFor = {ManagementForbiddenException.class, ManagementNotFoundException.class,
            ManagementConflictException.class, ManagementValidationException.class})
    public AgencyTask createTask(Account actor, String name, String description, String skillCode) {
        requireAgent(actor);
        if (management.taskExistsByName(name)) throw new ManagementConflictException("Esta tarefa já existe.");
        requireSkill(skillCode);
        AgencyTask task = management.saveTask(new AgencyTask(name, description, skillCode));
        management.record(actor, "TASK_CREATED", "AgencyTask",
                task.getId() == null ? null : task.getId().toString(), name);
        return task;
    }

    @Transactional(noRollbackFor = {ManagementForbiddenException.class, ManagementNotFoundException.class,
            ManagementValidationException.class})
    public AgencyTask updateTask(Account actor, UUID id, String name, String description, Boolean active,
            String skillCode) {
        requireAgent(actor);
        AgencyTask task = management.task(id).orElseThrow(ManagementNotFoundException::new);
        requireSkill(skillCode);
        task.update(name, description, active == null || active, skillCode);
        management.record(actor, "TASK_UPDATED", "AgencyTask", id.toString(), name);
        return task;
    }

    @Transactional(readOnly = true)
    public AgencyFinancePolicy financePolicy() {
        return management.financePolicy().orElseGet(AgencyFinancePolicy::new);
    }

    @Transactional(noRollbackFor = {ManagementForbiddenException.class})
    public AgencyFinancePolicy updateFinancePolicy(Account actor, int divisor, BigDecimal overtime,
            BigDecimal serviceRate, int dueDay, int validity, BigDecimal agencyFee, String auditDetails) {
        requireAgent(actor);
        AgencyFinancePolicy policy = management.financePolicy().orElseGet(AgencyFinancePolicy::new);
        policy.update(divisor, overtime, serviceRate, dueDay, validity, agencyFee);
        management.saveFinancePolicy(policy);
        management.record(actor, "FINANCE_POLICY_UPDATED", "AgencyFinancePolicy", "agency", auditDetails);
        return policy;
    }

    public boolean testDateEnabled() { return management.testDateEnabled(); }

    @Transactional(readOnly = true)
    public Map<String, Object> testDate() {
        if (!testDateEnabled()) throw new ManagementNotFoundException();
        return Map.of("serverDate", management.serverDate(), "testDateOverride",
                management.financePolicy().map(AgencyFinancePolicy::getTestDateOverride)
                        .map(LocalDate::toString).orElse(""));
    }

    @Transactional(noRollbackFor = {ManagementForbiddenException.class, ManagementNotFoundException.class})
    public Map<String, Object> updateTestDate(Account actor, LocalDate date) {
        requireAgent(actor);
        if (!testDateEnabled()) throw new ManagementNotFoundException();
        AgencyFinancePolicy policy = management.financePolicy().orElseGet(AgencyFinancePolicy::new);
        policy.setTestDateOverride(date);
        management.saveFinancePolicy(policy);
        management.record(actor, "TEST_DATE_OVERRIDE_UPDATED", "AgencyFinancePolicy", "agency",
                date == null ? "Data de teste removida" : "Data de teste definida: " + date);
        return testDate();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        List<EmploymentContract> contracts = management.contracts();
        List<HireRequest> requests = management.requests();
        List<PaymentObligation> payments = management.payments();
        List<IncidentReport> incidents = management.incidents();
        List<AbsenceRecord> absences = management.absences();
        List<AdditionalWork> extras = management.additionalWork();
        var clients = new HashSet<UUID>();
        var workers = new HashSet<UUID>();
        contracts.forEach(contract -> {
            clients.add(contract.getHireRequest().getClient().getId());
            workers.add(contract.getHireRequest().getHousekeeper().getId());
        });
        return Map.ofEntries(
                Map.entry("clients", clients.size()),
                Map.entry("candidates", workers.size()),
                Map.entry("contracts", contracts.size()),
                Map.entry("activeContracts", contracts.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count()),
                Map.entry("closedContracts", contracts.stream().filter(c -> !"ACTIVE".equals(c.getStatus())).count()),
                Map.entry("requestsPending", requests.stream().filter(r -> "PENDING".equals(r.getStatus())).count()),
                Map.entry("paymentsReceived", payments.stream().filter(p -> "PAGO".equals(p.getStatus()))
                        .map(PaymentObligation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)),
                Map.entry("paymentsPending", payments.stream().filter(p -> "PENDENTE".equals(p.getStatus()))
                        .map(PaymentObligation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)),
                Map.entry("absences", absences.size()),
                Map.entry("absencesPendingReview", absences.stream()
                        .filter(a -> Set.of("JUSTIFICACAO_SUBMETIDA", "EM_ANALISE").contains(a.getStatus())).count()),
                Map.entry("openIncidents", incidents.stream()
                        .filter(i -> Set.of("ABERTA", "EM_ANALISE", "AGUARDA_RESPOSTA").contains(i.getStatus())).count()),
                Map.entry("approvedAdditionalWork", extras.stream().filter(x -> "APPROVED".equals(x.getStatus()))
                        .map(AdditionalWork::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reports(LocalDate from, LocalDate to, UUID contractId, UUID clientId, UUID workerId) {
        List<EmploymentContract> all = management.contracts().stream()
                .filter(c -> contractId == null || c.getId().equals(contractId))
                .filter(c -> clientId == null || c.getHireRequest().getClient().getId().equals(clientId))
                .filter(c -> workerId == null || c.getHireRequest().getHousekeeper().getId().equals(workerId))
                .filter(c -> from == null || !c.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().isBefore(from))
                .filter(c -> to == null || !c.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(to)).toList();
        Set<UUID> scope = all.stream().map(EmploymentContract::getId).collect(java.util.stream.Collectors.toSet());
        List<PaymentObligation> payments = management.payments().stream()
                .filter(p -> scope.contains(p.getContract().getId())).toList();
        List<IncidentReport> incidents = management.incidents().stream()
                .filter(i -> scope.contains(i.getContract().getId())).toList();
        List<AbsenceRecord> absences = management.absences().stream()
                .filter(a -> scope.contains(a.getContract().getId())).toList();
        List<AdditionalWork> extras = management.additionalWork().stream()
                .filter(x -> scope.contains(x.getContract().getId())).toList();
        return Map.ofEntries(
                Map.entry("clients", all.stream().map(c -> c.getHireRequest().getClient().getId()).distinct().count()),
                Map.entry("candidates", all.stream().map(c -> c.getHireRequest().getHousekeeper().getId()).distinct().count()),
                Map.entry("contracts", all.size()),
                Map.entry("activeContracts", all.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count()),
                Map.entry("closedContracts", all.stream().filter(c -> !"ACTIVE".equals(c.getStatus())).count()),
                Map.entry("requestsPending", management.requests().stream()
                        .filter(r -> "PENDING".equals(r.getStatus())).count()),
                Map.entry("paymentsReceived", payments.stream().filter(p -> "PAGO".equals(p.getStatus()))
                        .map(PaymentObligation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)),
                Map.entry("paymentsPending", payments.stream().filter(p -> "PENDENTE".equals(p.getStatus()))
                        .map(PaymentObligation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)),
                Map.entry("absences", absences.size()),
                Map.entry("absencesPendingReview", absences.stream()
                        .filter(a -> Set.of("JUSTIFICACAO_SUBMETIDA", "EM_ANALISE").contains(a.getStatus())).count()),
                Map.entry("openIncidents", incidents.stream()
                        .filter(i -> Set.of("ABERTA", "EM_ANALISE", "AGUARDA_RESPOSTA").contains(i.getStatus())).count()),
                Map.entry("approvedAdditionalWork", extras.stream().filter(x -> "APPROVED".equals(x.getStatus()))
                        .map(AdditionalWork::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Transactional(readOnly = true)
    public List<UserNotification> notifications(UUID accountId) { return management.notifications(accountId); }

    @Transactional(noRollbackFor = {ManagementForbiddenException.class, ManagementNotFoundException.class})
    public UserNotification markNotificationRead(UUID id, UUID actorId) {
        UserNotification notification = management.notification(id).orElseThrow(ManagementNotFoundException::new);
        if (!notification.getAccount().getId().equals(actorId))
            throw new ManagementForbiddenException("");
        notification.markRead();
        return notification;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> auditEvents() { return management.auditEvents(); }

    private void requireSkill(String skillCode) {
        if (!SKILLS.contains(skillCode))
            throw new ManagementValidationException("Associe a tarefa a uma competência pesquisável.");
    }

    private void requireAgent(Account actor) {
        if (actor.getRole() != Role.AGENT) throw new ManagementForbiddenException("");
    }
}
