package com.agency.api.adapters.persistence;

import com.agency.api.application.requests.RequestCreationPort;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import com.agency.api.service.MozambiqueLocationCatalog;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RequestCreationPersistenceAdapter implements RequestCreationPort {
    private final AccountRepository accounts;
    private final ClientRepository clients;
    private final HousekeeperRepository housekeepers;
    private final HireRequestRepository requests;
    private final ContractRepository contracts;
    private final AgencyTaskRepository tasks;
    private final AgencyFinancePolicyRepository policies;
    private final MozambiqueLocationCatalog locations;
    private final AuditService audit;
    private final SalaryProposalRepository proposals;

    public RequestCreationPersistenceAdapter(
            AccountRepository accounts,
            ClientRepository clients,
            HousekeeperRepository housekeepers,
            HireRequestRepository requests,
            ContractRepository contracts,
            AgencyTaskRepository tasks,
            AgencyFinancePolicyRepository policies,
            MozambiqueLocationCatalog locations,
            AuditService audit,
            SalaryProposalRepository proposals) {
        this.accounts = accounts;
        this.clients = clients;
        this.housekeepers = housekeepers;
        this.requests = requests;
        this.contracts = contracts;
        this.tasks = tasks;
        this.policies = policies;
        this.locations = locations;
        this.audit = audit;
        this.proposals = proposals;
    }

    @Override
    public Optional<Account> findAccount(UUID id) {
        return accounts.findById(id);
    }

    @Override
    public boolean hasClientProfile(UUID accountId) {
        return clients.findByAccountId(accountId).isPresent();
    }

    @Override
    public Optional<HousekeeperProfile> findWorker(UUID id) {
        return housekeepers.findById(id);
    }

    @Override
    public boolean isValidLocation(String area, String district) {
        return locations.isValid(area, district);
    }

    @Override
    public String canonicalArea(String area) {
        return locations.canonicalArea(area);
    }

    @Override
    public Optional<Coordinates> coordinates(String area, String district) {
        return locations.coordinates(area, district)
                .map(point -> new Coordinates(point.latitude(), point.longitude()));
    }

    @Override
    public boolean isWorkerEmployed(UUID workerId) {
        return contracts.findAll().stream().anyMatch(contract ->
                "ACTIVE".equals(contract.getStatus())
                        && contract.getHireRequest().getHousekeeper().getId().equals(workerId));
    }

    @Override
    public Set<AgencyTask> activeTasks(Set<UUID> taskIds) {
        return tasks.findAllById(taskIds).stream().filter(AgencyTask::isActive).collect(Collectors.toSet());
    }

    @Override
    public boolean hasOpenRequest(UUID clientId, UUID workerId) {
        return requests.existsByClientIdAndHousekeeperIdAndStatusIn(clientId, workerId,
                Set.of("PENDING", "APPROVED", "EM_NEGOCIACAO"));
    }

    @Override
    public int requestValidityDays() {
        return policies.findById("agency").map(AgencyFinancePolicy::getRequestValidityDays).orElse(14);
    }

    @Override
    public HireRequest save(HireRequest request) {
        return requests.save(request);
    }

    @Override
    public void saveInitialProposal(HireRequest request, Account client, int monthlySalary) {
        proposals.save(new SalaryProposal(request, client, java.math.BigDecimal.valueOf(monthlySalary),
                "Oferta inicial do cliente"));
    }

    @Override
    public void record(Account actor, String action, String entityType, UUID entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }

    @Override
    public void notify(UUID accountId, String type, String message, String entityType, UUID entityId) {
        audit.notify(accountId, type, message, entityType, entityId);
    }
}
