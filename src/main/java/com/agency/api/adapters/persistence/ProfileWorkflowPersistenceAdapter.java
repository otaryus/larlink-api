package com.agency.api.adapters.persistence;

import com.agency.api.application.profiles.ProfileWorkflowPort;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import com.agency.api.service.MozambiqueLocationCatalog;
import com.agency.api.service.ServerDateService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProfileWorkflowPersistenceAdapter implements ProfileWorkflowPort {
    private final AccountRepository accounts;
    private final ClientRepository clients;
    private final HousekeeperRepository housekeepers;
    private final HireRequestRepository requests;
    private final ContractRepository contracts;
    private final IncidentRepository incidents;
    private final MozambiqueLocationCatalog locations;
    private final ServerDateService serverDate;
    private final AuditService audit;

    public ProfileWorkflowPersistenceAdapter(AccountRepository accounts, ClientRepository clients,
            HousekeeperRepository housekeepers, HireRequestRepository requests, ContractRepository contracts,
            IncidentRepository incidents,
            MozambiqueLocationCatalog locations, ServerDateService serverDate, AuditService audit) {
        this.accounts = accounts;
        this.clients = clients;
        this.housekeepers = housekeepers;
        this.requests = requests;
        this.contracts = contracts;
        this.incidents = incidents;
        this.locations = locations;
        this.serverDate = serverDate;
        this.audit = audit;
    }

    @Override public Optional<Account> account(UUID id) { return accounts.findById(id); }
    @Override public Optional<Account> accountByEmail(String email) { return accounts.findByEmailIgnoreCase(email); }
    @Override public Account saveAccount(Account account) { return accounts.save(account); }
    @Override public Optional<ClientProfile> clientProfile(UUID accountId) { return clients.findByAccountId(accountId); }
    @Override public ClientProfile saveClient(ClientProfile profile) { return clients.save(profile); }
    @Override public Optional<HousekeeperProfile> housekeeperProfile(UUID accountId) {
        return housekeepers.findByAccountId(accountId);
    }
    @Override public Optional<HousekeeperProfile> housekeeper(UUID id) { return housekeepers.findById(id); }
    @Override public boolean phoneExists(String phone) { return housekeepers.existsByPhone(phone); }
    @Override public boolean phoneExistsForOther(String phone, UUID id) {
        return housekeepers.existsByPhoneAndIdNot(phone, id);
    }
    @Override public HousekeeperProfile saveHousekeeper(HousekeeperProfile profile) {
        return housekeepers.save(profile);
    }
    @Override public List<HousekeeperProfile> housekeepers() { return housekeepers.findAll(); }
    @Override public List<EmploymentContract> contracts() { return contracts.findAll(); }
    @Override public List<HireRequest> requestsForClient(UUID id) { return requests.findByClientIdOrderByCreatedAtDesc(id); }
    @Override public List<HireRequest> requestsForAgency() { return requests.findAllByOrderByCreatedAtDesc(); }
    @Override public List<HireRequest> requestsForWorker(UUID id) { return requests.findByWorkerAccountId(id); }
    @Override public List<EmploymentContract> contractsForClient(UUID id) { return contracts.findForClient(id); }
    @Override public List<EmploymentContract> contractsForWorker(UUID id) { return contracts.findForWorker(id); }
    @Override public List<EmploymentContract> contractsForAgency() { return contracts.findAllByOrderByCreatedAtDesc(); }
    @Override public long clientCount() { return clients.count(); }
    @Override public List<IncidentReport> incidents() { return incidents.findAll(); }
    @Override public boolean validLocation(String city, String district) { return locations.isValid(city, district); }
    @Override public LocalDate today() { return serverDate.currentDate(); }
    @Override public boolean testDateEnabled() { return serverDate.isTestDateEnabled(); }
    @Override public void record(Account actor, String action, String entityType, UUID entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }
    @Override public void notify(UUID accountId, String type, String message, String entityType, UUID entityId) {
        audit.notify(accountId, type, message, entityType, entityId);
    }
}
