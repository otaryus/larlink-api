package com.agency.api.application.requests;

import com.agency.api.domain.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RequestCreationPort {
    Optional<Account> findAccount(UUID id);

    boolean hasClientProfile(UUID accountId);

    Optional<HousekeeperProfile> findWorker(UUID id);

    boolean isValidLocation(String area, String district);

    String canonicalArea(String area);

    Optional<Coordinates> coordinates(String area, String district);

    boolean isWorkerEmployed(UUID workerId);

    Set<AgencyTask> activeTasks(Set<UUID> taskIds);

    boolean hasOpenRequest(UUID clientId, UUID workerId);

    int requestValidityDays();

    HireRequest save(HireRequest request);

    void saveInitialProposal(HireRequest request, Account client, int monthlySalary);

    void record(Account actor, String action, String entityType, UUID entityId, String details);

    void notify(UUID accountId, String type, String message, String entityType, UUID entityId);

    record Coordinates(double latitude, double longitude) {
    }
}
