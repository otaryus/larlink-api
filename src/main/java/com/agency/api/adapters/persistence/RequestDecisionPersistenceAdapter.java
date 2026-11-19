package com.agency.api.adapters.persistence;

import com.agency.api.application.requests.ContractDraft;
import com.agency.api.application.requests.RequestDecisionPort;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import com.agency.api.service.ContractDocumentService;
import com.agency.api.service.ContractDraftService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RequestDecisionPersistenceAdapter implements RequestDecisionPort {
    private final HireRequestRepository requests;
    private final SalaryProposalRepository proposals;
    private final AccountRepository accounts;
    private final ContractRepository contracts;
    private final ContractDraftService drafts;
    private final ContractDocumentService documents;
    private final AuditService audit;

    public RequestDecisionPersistenceAdapter(
            HireRequestRepository requests,
            SalaryProposalRepository proposals,
            AccountRepository accounts,
            ContractRepository contracts,
            ContractDraftService drafts,
            ContractDocumentService documents,
            AuditService audit) {
        this.requests = requests;
        this.proposals = proposals;
        this.accounts = accounts;
        this.contracts = contracts;
        this.drafts = drafts;
        this.documents = documents;
        this.audit = audit;
    }

    @Override
    public Optional<HireRequest> lockRequest(UUID requestId) {
        return requests.findLockedById(requestId);
    }

    @Override
    public Optional<SalaryProposal> findProposal(UUID proposalId) {
        return proposals.findById(proposalId);
    }

    @Override
    public Optional<Account> findAccount(UUID accountId) {
        return accounts.findById(accountId);
    }

    @Override
    public List<SalaryProposal> proposals(UUID requestId) {
        return proposals.findByHireRequestIdOrderByCreatedAtAsc(requestId);
    }

    @Override
    public SalaryProposal saveProposal(SalaryProposal proposal) {
        return proposals.save(proposal);
    }

    @Override
    public boolean hasContract(UUID requestId) {
        return contracts.existsByHireRequestId(requestId);
    }

    @Override
    public ContractDraft generateDraft(HireRequest request) {
        var draft = drafts.generate(request);
        return new ContractDraft(draft.text(), draft.aiGenerated());
    }

    @Override
    public EmploymentContract saveContract(EmploymentContract contract) {
        return contracts.save(contract);
    }

    @Override
    public void ensureInitialContractVersion(EmploymentContract contract) {
        documents.ensureInitialVersion(contract);
    }

    @Override
    public void record(Account actor, String action, String entityType, UUID entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }

    @Override
    public void notify(UUID accountId, String type, String message, String entityType, UUID entityId) {
        audit.notify(accountId, type, message, entityType, entityId);
    }

    @Override
    public void notifyRole(Role role, String type, String message, String entityType, UUID entityId) {
        audit.notifyRole(role, type, message, entityType, entityId);
    }
}
