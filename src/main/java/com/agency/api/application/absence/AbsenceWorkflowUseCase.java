package com.agency.api.application.absence;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AbsenceWorkflowUseCase {
    private static final String ENTITY = "AbsenceRecord";
    private final AbsenceWorkflowPort workflow;

    public AbsenceWorkflowUseCase(AbsenceWorkflowPort workflow) {
        this.workflow = workflow;
    }

    @Transactional
    public AbsenceDetails report(UUID actorId, UUID contractId, LocalDate date, BigDecimal days, String notes) {
        Account actor = account(actorId);
        EmploymentContract contract = workflow.findContract(contractId).orElseThrow(AbsenceNotFoundException::new);
        if (!contract.getHireRequest().getClient().getId().equals(actor.getId())) {
            throw new AbsenceForbiddenException("Só o cliente do contrato pode registar a falta.");
        }
        AbsenceRecord absence = workflow.save(new AbsenceRecord(contract, actor, date, days, notes));
        workflow.record(actor, "ABSENCE_REPORTED", ENTITY, absence.getId(), date.toString());
        workflow.notify(contract.getHireRequest().getHousekeeper().getAccount().getId(),
                "ABSENCE_REPORTED", "Foi registada uma falta no contrato.", ENTITY, absence.getId());
        workflow.notifyRole(Role.AGENT, "ABSENCE_REPORTED",
                "Foi registada uma falta para análise.", ENTITY, absence.getId());
        return details(absence);
    }

    @Transactional(readOnly = true)
    public List<AbsenceDetails> list(UUID actorId) {
        Account actor = account(actorId);
        return workflow.findAll().stream()
                .filter(absence -> actor.getRole() == Role.AGENT || actor.getRole() == Role.SECRETARY
                        || absence.getContract().getHireRequest().getClient().getId().equals(actor.getId())
                        || absence.getContract().getHireRequest().getHousekeeper().getAccount().getId().equals(actor.getId()))
                .map(this::details)
                .toList();
    }

    @Transactional
    public AbsenceDetails submitJustification(UUID absenceId, UUID actorId, String text, byte[] evidence, String contentType) {
        AbsenceRecord absence = absence(absenceId);
        Account actor = account(actorId);
        requireWorker(absence, actor);
        try {
            absence.submitJustification(text == null ? "" : text.trim(), evidence, contentType);
        } catch (IllegalStateException exception) {
            throw new AbsenceInvalidStateException(exception.getMessage());
        }
        workflow.record(actor, "ABSENCE_JUSTIFICATION_SUBMITTED", ENTITY, absenceId, "Justificação submetida");
        workflow.notifyRole(Role.AGENT, "ABSENCE_JUSTIFICATION",
                "Uma justificação aguarda análise.", ENTITY, absenceId);
        return details(absence);
    }

    @Transactional
    public AbsenceDetails declareNoJustification(UUID absenceId, UUID actorId) {
        AbsenceRecord absence = absence(absenceId);
        Account actor = account(actorId);
        requireWorker(absence, actor);
        try {
            absence.declareNoJustification();
        } catch (IllegalStateException exception) {
            throw new AbsenceInvalidStateException(exception.getMessage());
        }
        workflow.record(actor, "ABSENCE_NO_JUSTIFICATION_DECLARED", ENTITY, absenceId,
                "Trabalhadora declarou não possuir justificação");
        workflow.notifyRole(Role.AGENT, "ABSENCE_NO_JUSTIFICATION",
                "Uma trabalhadora declarou não possuir justificação para uma falta.", ENTITY, absenceId);
        workflow.notify(absence.getContract().getHireRequest().getClient().getId(), "ABSENCE_NO_JUSTIFICATION",
                "A trabalhadora informou que não possui justificação para uma falta.", ENTITY, absenceId);
        return details(absence);
    }

    @Transactional
    public AbsenceDetails decide(UUID absenceId, UUID actorId, String decision, String note) {
        AbsenceRecord absence = absence(absenceId);
        Account actor = account(actorId);
        String normalizedDecision = decision.toUpperCase(Locale.ROOT);
        try {
            absence.decide(normalizedDecision, note, actor);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new AbsenceInvalidStateException(exception.getMessage());
        }
        workflow.record(actor, "ABSENCE_DECIDED", ENTITY, absenceId,
                decision + " " + (note == null ? "" : note));
        workflow.notify(absence.getContract().getHireRequest().getHousekeeper().getAccount().getId(),
                "ABSENCE_DECISION", "A agência analisou a justificação da falta: "
                        + absence.getStatus() + ".", ENTITY, absenceId);
        return details(absence);
    }

    @Transactional(readOnly = true)
    public AbsenceEvidence evidence(UUID absenceId, UUID actorId) {
        AbsenceRecord absence = absence(absenceId);
        if (absence.getEvidence() == null) throw new AbsenceNotFoundException();
        Account actor = account(actorId);
        if (actor.getRole() != Role.AGENT) requireWorker(absence, actor);
        return new AbsenceEvidence(absence.getEvidence(), absence.getEvidenceContentType());
    }

    private Account account(UUID id) {
        return workflow.findAccount(id).orElseThrow(AbsenceNotFoundException::new);
    }

    private AbsenceRecord absence(UUID id) {
        return workflow.findAbsence(id).orElseThrow(AbsenceNotFoundException::new);
    }

    private void requireWorker(AbsenceRecord absence, Account actor) {
        if (!absence.getContract().getHireRequest().getHousekeeper().getAccount().getId().equals(actor.getId())) {
            throw new AbsenceForbiddenException("Esta falta não pertence ao seu contrato.");
        }
    }

    private AbsenceDetails details(AbsenceRecord absence) {
        HireRequest request = absence.getContract().getHireRequest();
        return new AbsenceDetails(
                absence.getId(),
                absence.getContract().getId(),
                request.getClient().getName(),
                request.getHousekeeper().getName(),
                absence.getReportedBy().getName(),
                absence.getAbsenceDate(),
                absence.getDays(),
                absence.getNotes() == null ? "" : absence.getNotes(),
                absence.getStatus(),
                absence.getJustification() == null ? "" : absence.getJustification(),
                absence.getEvidence() != null,
                absence.getEvidenceContentType() == null ? "" : absence.getEvidenceContentType(),
                absence.getAgencyDecisionNote() == null ? "" : absence.getAgencyDecisionNote(),
                absence.getCreatedAt());
    }
}
