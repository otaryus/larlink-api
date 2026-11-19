package com.agency.api.application.contracts;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
public class ContractLifecycleUseCase {
    private final ContractLifecyclePort lifecycle;

    public ContractLifecycleUseCase(ContractLifecyclePort lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Transactional
    public EmploymentContract sign(UUID contractId, UUID actorId, String fullName, boolean contractRead) {
        EmploymentContract contract = lifecycle.lockContract(contractId).orElseThrow(ContractLifecycleNotFoundException::new);
        Account actor = account(actorId);
        if (!contract.isAgencyReviewed()) {
            throw new ContractValidationException("A agência precisa rever a minuta antes das assinaturas.");
        }
        HireRequest request = contract.getHireRequest();
        if (request.getClient().getId().equals(actorId)) {
            validateSignatureName(actor, fullName, contractRead);
            invoke(() -> contract.signClient(fullName));
        } else if (request.getHousekeeper().getAccount().getId().equals(actorId)) {
            validateSignatureName(actor, fullName, contractRead);
            invoke(() -> contract.signWorker(fullName));
        } else {
            throw new ContractForbiddenException("Este contrato não pertence à sua conta.");
        }
        lifecycle.record(actor, "CONTRACT_SIGNED", "EmploymentContract", contractId,
                actorId.equals(request.getClient().getId()) ? "Cliente assinou" : "Trabalhadora assinou");
        if (contract.isClientSigned() && contract.isWorkerSigned()) {
            lifecycle.notify(request.getClient().getId(), "CONTRACT_ACTIVE",
                    "Contrato assinado pelas partes.", "EmploymentContract", contractId);
            lifecycle.notify(request.getHousekeeper().getAccount().getId(), "CONTRACT_ACTIVE",
                    "Contrato assinado pelas partes.", "EmploymentContract", contractId);
        }
        return contract;
    }

    @Transactional
    public EmploymentContract confirmStarted(UUID contractId, UUID actorId) {
        EmploymentContract contract = lifecycle.lockContract(contractId).orElseThrow(ContractLifecycleNotFoundException::new);
        Account actor = account(actorId);
        HireRequest request = contract.getHireRequest();
        if (!"ACTIVE".equals(contract.getStatus())) {
            throw new ContractValidationException("As duas partes devem assinar o contrato antes de confirmar o início.");
        }
        if (request.getStartDate().isAfter(lifecycle.currentDate())) {
            throw new ContractValidationException("A confirmação do início só fica disponível a partir de "
                    + request.getStartDate() + ".");
        }
        boolean alreadyStarted = contract.isWorkStarted();
        if (request.getClient().getId().equals(actorId)) {
            contract.confirmStartedByClient();
        } else if (request.getHousekeeper().getAccount().getId().equals(actorId)) {
            contract.confirmStartedByWorker();
        } else {
            throw new ContractForbiddenException("Este contrato não pertence à sua conta.");
        }
        if (contract.isWorkStarted()) {
            request.getHousekeeper().getAccount().markAsHousekeeper();
            request.getHousekeeper().decide("EMPLOYED", null);
            if (!alreadyStarted) {
                lifecycle.notifyRole(Role.AGENT, "WORK_START_CONFIRMED",
                        "As duas partes confirmaram o início do trabalho.", "EmploymentContract", contractId);
                lifecycle.notify(request.getClient().getId(), "WORK_START_CONFIRMED",
                        "As duas partes confirmaram o início do trabalho.", "EmploymentContract", contractId);
                lifecycle.notify(request.getHousekeeper().getAccount().getId(), "WORK_START_CONFIRMED",
                        "As duas partes confirmaram o início do trabalho.", "EmploymentContract", contractId);
            }
        }
        lifecycle.record(actor, "CONTRACT_START_CONFIRMED", "EmploymentContract", contractId,
                actorId.equals(request.getClient().getId()) ? "Cliente confirmou o início" : "Trabalhadora confirmou o início");
        return contract;
    }

    @Transactional
    public EmploymentContract review(UUID contractId, UUID actorId) {
        EmploymentContract contract = lifecycle.findContract(contractId).orElseThrow(ContractLifecycleNotFoundException::new);
        Account actor = account(actorId);
        contract.reviewByAgency();
        lifecycle.record(actor, "CONTRACT_REVIEWED", "EmploymentContract", contractId,
                "Revisão da agência registada");
        return contract;
    }

    @Transactional
    public ContractReviewResult decideReview(UUID contractId, UUID actorId, boolean approved, String note) {
        EmploymentContract contract = lifecycle.findContract(contractId).orElseThrow(ContractLifecycleNotFoundException::new);
        Account actor = account(actorId);
        String normalizedNote = note == null ? "" : note.trim();
        try {
            if (approved) contract.reviewByAgency();
            else contract.requestAgencyChanges(normalizedNote);
        } catch (IllegalStateException exception) {
            throw new ContractValidationException(exception.getMessage());
        }
        HireRequest request = contract.getHireRequest();
        lifecycle.record(actor, approved ? "CONTRACT_REVIEW_APPROVED" : "CONTRACT_CHANGES_REQUESTED",
                "EmploymentContract", contractId, note);
        String notice = approved ? "A agência aprovou a minuta. Já pode ser assinada."
                : "A agência pediu alterações na minuta: " + note;
        lifecycle.notify(request.getClient().getId(), "CONTRACT_REVIEW", notice, "EmploymentContract", contractId);
        lifecycle.notify(request.getHousekeeper().getAccount().getId(), "CONTRACT_REVIEW",
                notice, "EmploymentContract", contractId);
        return new ContractReviewResult(contract.isAgencyReviewed(), contract.getAgencyReviewState(),
                Optional.ofNullable(contract.getAgencyReviewNote()).orElse(""));
    }

    @Transactional
    public EmploymentContract terminate(UUID contractId, UUID actorId, String reason) {
        EmploymentContract contract = lifecycle.findContract(contractId).orElseThrow(ContractLifecycleNotFoundException::new);
        Account actor = account(actorId);
        String normalizedReason = reason.trim();
        try {
            contract.terminate(normalizedReason);
        } catch (IllegalStateException exception) {
            throw new ContractValidationException(exception.getMessage());
        }
        HireRequest request = contract.getHireRequest();
        HousekeeperProfile profile = request.getHousekeeper();
        if (!lifecycle.hasOtherActiveContract(contractId, profile.getId())) profile.decide("APPROVED", null);
        lifecycle.record(actor, "CONTRACT_TERMINATED", "EmploymentContract", contractId, normalizedReason);
        lifecycle.notify(request.getClient().getId(), "CONTRACT_TERMINATED",
                "O contrato foi encerrado pela agência.", "EmploymentContract", contractId);
        lifecycle.notify(profile.getAccount().getId(), "CONTRACT_TERMINATED",
                "O contrato foi encerrado pela agência.", "EmploymentContract", contractId);
        return contract;
    }

    @Transactional
    public ContractRevision revise(UUID contractId, UUID actorId, String content, String reason) {
        EmploymentContract contract = lifecycle.findContract(contractId).orElseThrow(ContractLifecycleNotFoundException::new);
        Account actor = account(actorId);
        boolean addendum = contract.isClientSigned() || contract.isWorkerSigned() || "ACTIVE".equals(contract.getStatus());
        try {
            if (!addendum) contract.reviseDraft(content);
            ContractRevision revision = lifecycle.addVersion(contract, actor, content, reason, addendum);
            lifecycle.record(actor, addendum ? "CONTRACT_ADDENDUM_CREATED" : "CONTRACT_DRAFT_REVISED",
                    "ContractRevision", revision.getId(), reason);
            return revision;
        } catch (IllegalStateException exception) {
            throw new ContractValidationException(exception.getMessage());
        }
    }

    @Transactional
    public List<ContractRevision> history(UUID contractId, UUID actorId) {
        EmploymentContract contract = lifecycle.findContract(contractId)
                .orElseThrow(ContractLifecycleNotFoundException::new);
        Account actor = account(actorId);
        requireViewer(contract, actor);
        lifecycle.ensureInitialVersion(contract);
        return lifecycle.history(contractId);
    }

    @Transactional
    public byte[] pdf(UUID contractId, UUID actorId) {
        EmploymentContract contract = lifecycle.findContract(contractId)
                .orElseThrow(ContractLifecycleNotFoundException::new);
        Account actor = account(actorId);
        requireViewer(contract, actor);
        lifecycle.ensureInitialVersion(contract);
        return lifecycle.pdf(contract);
    }

    private void requireViewer(EmploymentContract contract, Account actor) {
        HireRequest request = contract.getHireRequest();
        if (actor.getRole() != Role.AGENT && !request.getClient().getId().equals(actor.getId())
                && !request.getHousekeeper().getAccount().getId().equals(actor.getId())) {
            throw new ContractForbiddenException("Este contrato não pertence à sua conta.");
        }
    }

    private Account account(UUID id) {
        return lifecycle.findAccount(id).orElseThrow(ContractLifecycleNotFoundException::new);
    }

    private void validateSignatureName(Account actor, String name, boolean read) {
        if (!actor.getName().equals(name)) {
            throw new ContractValidationException("Digite o nome completo exactamente como está no registo.");
        }
        if (!read) throw new ContractValidationException("Confirme que leu e concorda com o contrato.");
    }

    private void invoke(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new ContractConflictException(exception.getMessage());
        }
    }
}
