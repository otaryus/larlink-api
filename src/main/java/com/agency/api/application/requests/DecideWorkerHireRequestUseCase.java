package com.agency.api.application.requests;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class DecideWorkerHireRequestUseCase {
    private final RequestDecisionPort decisions;

    public DecideWorkerHireRequestUseCase(RequestDecisionPort decisions) {
        this.decisions = decisions;
    }

    @Transactional
    public WorkerRequestDecisionResult decide(UUID requestId, UUID workerAccountId, boolean accepted) {
        HireRequest request = decisions.lockRequest(requestId).orElseThrow(RequestNotFoundException::new);
        Account worker = decisions.findAccount(workerAccountId).orElseThrow(RequestNotFoundException::new);
        if (!request.getHousekeeper().getAccount().getId().equals(worker.getId())) {
            throw new RequestForbiddenException("Este convite não pertence ao seu perfil.");
        }
        if (!Set.of("PENDING", "APPROVED").contains(request.getStatus())) {
            throw new RequestValidationException("Este pedido já não está disponível para resposta.");
        }
        if (!accepted) {
            request.decide("WORKER_DECLINED", null);
            decisions.proposals(requestId).stream()
                    .filter(proposal -> "OPEN".equals(proposal.getStatus()))
                    .forEach(proposal -> proposal.mark("REJECTED"));
            decisions.record(worker, "HIRE_REQUEST_DECLINED", "HireRequest", requestId,
                    "Convite recusado pela candidata");
            decisions.notify(request.getClient().getId(), "PROPOSAL_REJECTED",
                    "A candidata recusou o convite.", "HireRequest", requestId);
            return new WorkerRequestDecisionResult(request, null);
        }
        if (decisions.hasContract(requestId)) {
            throw new RequestConflictException("Já existe um contrato para este pedido.");
        }
        request.decide("WORKER_ACCEPTED", null);
        decisions.proposals(requestId).stream()
                .filter(proposal -> "OPEN".equals(proposal.getStatus()))
                .reduce((first, second) -> second)
                .ifPresent(proposal -> proposal.mark("ACCEPTED"));
        ContractDraft draft = decisions.generateDraft(request);
        EmploymentContract contract = decisions.saveContract(
                new EmploymentContract(request, draft.text(), draft.aiGenerated()));
        decisions.ensureInitialContractVersion(contract);
        decisions.record(worker, "HIRE_REQUEST_ACCEPTED", "HireRequest", requestId,
                "Convite e salário inicial aceites");
        decisions.notify(request.getClient().getId(), "CONTRACT_READY",
                "A minuta do contrato está disponível para revisão.", "EmploymentContract", contract.getId());
        decisions.notifyRole(Role.AGENT, "CONTRACT_READY",
                "Um contrato aguarda revisão da agência.", "EmploymentContract", contract.getId());
        return new WorkerRequestDecisionResult(request, contract);
    }
}
