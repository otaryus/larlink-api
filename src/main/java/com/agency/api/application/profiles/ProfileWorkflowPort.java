package com.agency.api.application.profiles;

import com.agency.api.domain.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileWorkflowPort {
    Optional<Account> account(UUID id);
    Optional<Account> accountByEmail(String email);
    Account saveAccount(Account account);
    Optional<ClientProfile> clientProfile(UUID accountId);
    ClientProfile saveClient(ClientProfile profile);
    Optional<HousekeeperProfile> housekeeperProfile(UUID accountId);
    Optional<HousekeeperProfile> housekeeper(UUID id);
    boolean phoneExists(String phone);
    boolean phoneExistsForOther(String phone, UUID profileId);
    HousekeeperProfile saveHousekeeper(HousekeeperProfile profile);
    List<HousekeeperProfile> housekeepers();
    List<EmploymentContract> contracts();
    List<HireRequest> requestsForClient(UUID clientId);
    List<HireRequest> requestsForAgency();
    List<HireRequest> requestsForWorker(UUID accountId);
    List<EmploymentContract> contractsForClient(UUID accountId);
    List<EmploymentContract> contractsForWorker(UUID accountId);
    List<EmploymentContract> contractsForAgency();
    long clientCount();
    List<IncidentReport> incidents();
    boolean validLocation(String city, String district);
    LocalDate today();
    boolean testDateEnabled();
    void record(Account actor, String action, String entityType, UUID entityId, String details);
    void notify(UUID accountId, String type, String message, String entityType, UUID entityId);
}
