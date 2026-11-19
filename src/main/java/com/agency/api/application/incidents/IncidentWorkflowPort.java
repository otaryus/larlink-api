package com.agency.api.application.incidents;

import com.agency.api.domain.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentWorkflowPort {
    Optional<Account> findAccount(UUID id);

    Optional<EmploymentContract> findContract(UUID id);

    Optional<IncidentReport> findIncident(UUID id);

    Optional<IncidentMessage> findMessage(UUID id);

    List<IncidentReport> findAllIncidents();

    List<IncidentMessage> messages(UUID incidentId);

    IncidentReport saveIncident(IncidentReport incident);

    IncidentMessage saveMessage(IncidentMessage message);

    void record(Account actor, String action, String entityType, UUID entityId, String details);

    void notify(UUID accountId, String type, String message, String entityType, UUID entityId);

    void notifyRole(Role role, String type, String message, String entityType, UUID entityId);
}
