package com.agency.api.application.requests;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@Service
public class DecideSalaryProposalUseCase {
    private final RequestDecisionPort decisions;

    public DecideSalaryProposalUseCase(RequestDecisionPort decisions) {
        this.decisions = decisions;
    }

    @Transactional(readOnly = true)
    public List<SalaryProposal> history(UUID requestId, UUID actorId) {
        HireRequest request = decisions.lockRequest(requestId).orElseThrow(RequestNotFoundException::new);
        Account actor = decisions.findAccount(actorId).orElseThrow(RequestNotFoundException::new);
        boolean party = request.getClient().getId().equals(actorId)
                || request.getHousekeeper().getAccount().getId().equals(actorId);
        if (actor.getRole() != Role.AGENT && !party)
            throw new RequestForbiddenException("Sem acesso a este pedido.");
        return decisions.proposals(requestId);
    }

    @Transactional
    public SalaryProposalDecisionResult decide(
            UUID requestId, UUID proposalId, UUID actorId, String decision, String message) {
        HireRequest request = decisions.lockRequest(requestId).orElseThrow(RequestNotFoundException::new);
        Account actor = decisions.findAccount(actorId).orElseThrow(RequestNotFoundException::new);
        SalaryProposal proposal = decisions.findProposal(proposalId).orElseThrow(RequestNotFoundException::new);
        if (!requestId.equals(proposal.getHireRequest().getId())) throw new RequestNotFoundException();
        boolean party = request.getClient().getId().equals(actorId)
                || request.getHousekeeper().getAccount().getId().equals(actorId);
        if (!party || proposal.getAuthor().getId().equals(actorId)) {
            throw new RequestForbiddenException("Só a outra parte pode responder à proposta.");
        }
        if (!"OPEN".equals(proposal.getStatus())) {
            throw new RequestConflictException("Esta proposta já não está aberta.");
        }
        String normalized = decision.toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("ACCEPTED", "REJECTED").contains(normalized)) {
            throw new RequestValidationException("Escolha aceitar ou rejeitar.");
        }

        proposal.mark(normalized);
        EmploymentContract contract = null;
        if ("ACCEPTED".equals(normalized)) {
            decisions.proposals(requestId).stream()
                    .filter(other -> !other.getId().equals(proposal.getId()) && "OPEN".equals(other.getStatus()))
                    .forEach(other -> other.mark("CLOSED"));
            int agreedSalary = proposal.getMonthlySalary().setScale(0, RoundingMode.HALF_UP).intValueExact();
            request.setMonthlySalary(agreedSalary);
            request.setStatus("CONTRATO_GERADO");
            if (!decisions.hasContract(requestId)) {
                ContractDraft draft = decisions.generateDraft(request);
                contract = decisions.saveContract(new EmploymentContract(request, draft.text(), draft.aiGenerated()));
                decisions.ensureInitialContractVersion(contract);
            }
            decisions.notify(request.getClient().getId(), "SALARY_ACCEPTED",
                    "A proposta salarial foi aceite. A minuta do contrato foi gerada.", "HireRequest", requestId);
            decisions.notify(request.getHousekeeper().getAccount().getId(), "SALARY_ACCEPTED",
                    "A proposta salarial foi aceite. A minuta do contrato foi gerada.", "HireRequest", requestId);
            decisions.notifyRole(Role.AGENT, "CONTRACT_READY",
                    "Uma negociação salarial foi confirmada e a minuta aguarda revisão.",
                    "EmploymentContract", contract == null ? null : contract.getId());
        } else {
            request.setStatus("EM_NEGOCIACAO");
            decisions.notify(proposal.getAuthor().getId(), "SALARY_REJECTED",
                    "A proposta salarial foi rejeitada. Pode enviar outra proposta.", "HireRequest", requestId);
        }
        decisions.record(actor, "SALARY_PROPOSAL_" + normalized, "SalaryProposal", proposalId, message);
        return new SalaryProposalDecisionResult(proposal, request.getStatus(), contract);
    }

    @Transactional
    public SalaryProposal counterOffer(UUID requestId, UUID actorId, BigDecimal monthlySalary, String message) {
        HireRequest request = decisions.lockRequest(requestId).orElseThrow(RequestNotFoundException::new);
        Account actor = decisions.findAccount(actorId).orElseThrow(RequestNotFoundException::new);
        boolean clientProposal = request.getClient().getId().equals(actorId);
        boolean workerProposal = request.getHousekeeper().getAccount().getId().equals(actorId);
        if (!clientProposal && !workerProposal) {
            throw new RequestForbiddenException("Este pedido não pertence à sua conta.");
        }
        if (!Set.of("PENDING", "APPROVED", "EM_NEGOCIACAO", "WORKER_DECLINED").contains(request.getStatus())) {
            throw new RequestValidationException("A negociação já não está disponível.");
        }
        if (workerProposal && monthlySalary.compareTo(BigDecimal.valueOf(request.getMonthlySalary())) < 0) {
            throw new RequestValidationException("A proposta da trabalhadora não pode ser inferior à oferta actual.");
        }
        if (clientProposal && monthlySalary.compareTo(BigDecimal.valueOf(request.getHousekeeper().getExpectedSalary())) >= 0) {
            throw new RequestValidationException("A negociação só está disponível quando a oferta é inferior ao salário pretendido pela trabalhadora.");
        }
        if (monthlySalary.remainder(BigDecimal.valueOf(50)).compareTo(BigDecimal.ZERO) != 0) {
            throw new RequestValidationException("O salário proposto deve ser um múltiplo de 50 MT.");
        }
        var history = decisions.proposals(requestId);
        if (history.stream().anyMatch(proposal -> "ACCEPTED".equals(proposal.getStatus()))) {
            throw new RequestConflictException("A negociação já foi encerrada.");
        }
        var open = history.stream().filter(proposal -> "OPEN".equals(proposal.getStatus())).toList();
        if (open.stream().anyMatch(proposal -> proposal.getAuthor().getId().equals(actorId))) {
            throw new RequestValidationException("Aguarde a resposta à sua proposta actual.");
        }
        open.forEach(proposal -> proposal.mark("COUNTERED"));
        SalaryProposal proposal = decisions.saveProposal(new SalaryProposal(request, actor, monthlySalary, message));
        request.setStatus("EM_NEGOCIACAO");
        decisions.record(actor, "SALARY_PROPOSAL_CREATED", "HireRequest", requestId, monthlySalary.toPlainString());
        Account recipient = clientProposal ? request.getHousekeeper().getAccount() : request.getClient();
        decisions.notify(recipient.getId(), "SALARY_PROPOSAL", "Recebeu uma nova proposta salarial.",
                "HireRequest", requestId);
        return proposal;
    }
}
