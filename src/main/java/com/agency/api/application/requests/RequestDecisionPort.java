package com.agency.api.application.requests;

import com.agency.api.domain.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestDecisionPort {
    Optional<HireRequest> lockRequest(UUID requestId);

    Optional<SalaryProposal> findProposal(UUID proposalId);

    Optional<Account> findAccount(UUID accountId);

    List<SalaryProposal> proposals(UUID requestId);

    SalaryProposal saveProposal(SalaryProposal proposal);

    boolean hasContract(UUID requestId);

    ContractDraft generateDraft(HireRequest request);

    EmploymentContract saveContract(EmploymentContract contract);

    void ensureInitialContractVersion(EmploymentContract contract);

    void record(Account actor, String action, String entityType, UUID entityId, String details);

    void notify(UUID accountId, String type, String message, String entityType, UUID entityId);

    void notifyRole(Role role, String type, String message, String entityType, UUID entityId);
}
