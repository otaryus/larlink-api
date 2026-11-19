package com.agency.api.application.management;

import com.agency.api.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagementPort {
    List<AgencyTask> activeTasks();
    boolean taskExistsByName(String name);
    Optional<AgencyTask> task(UUID id);
    AgencyTask saveTask(AgencyTask task);
    Optional<AgencyFinancePolicy> financePolicy();
    AgencyFinancePolicy saveFinancePolicy(AgencyFinancePolicy policy);
    LocalDate serverDate();
    boolean testDateEnabled();
    void record(Account actor, String action, String entityType, String entityId, String details);

    List<EmploymentContract> contracts();
    List<HireRequest> requests();
    List<PaymentObligation> payments();
    List<IncidentReport> incidents();
    List<AbsenceRecord> absences();
    List<AdditionalWork> additionalWork();

    List<UserNotification> notifications(UUID accountId);
    Optional<UserNotification> notification(UUID id);
    List<AuditEvent> auditEvents();
}
