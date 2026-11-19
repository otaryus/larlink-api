package com.agency.api.application.incidents;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class IncidentWorkflowUseCase {
    private static final Set<String> TYPES = Set.of("CONTRACT_BREACH", "CLIENT_COMPLAINT", "WORKER_COMPLAINT",
            "INAPPROPRIATE_BEHAVIOR", "SCHEDULE_CHANGE", "OVERTIME", "OUT_OF_SCOPE_TASK", "ADDITIONAL_SERVICE", "OTHER");
    private static final Set<String> OPEN_STATUSES = Set.of("ABERTA", "EM_ANALISE", "AGUARDA_RESPOSTA");
    private final IncidentWorkflowPort incidents;

    public IncidentWorkflowUseCase(IncidentWorkflowPort incidents) {
        this.incidents = incidents;
    }

    @Transactional
    public IncidentReport report(UUID actorId, UUID contractId, String type, String description) {
        EmploymentContract contract = incidents.findContract(contractId).orElseThrow(IncidentNotFoundException::new);
        Account actor = account(actorId);
        if (!isParty(contract.getHireRequest(), actor)) {
            throw new IncidentForbiddenException("Só as partes do contrato podem reportar ocorrências.");
        }
        String normalizedType = type == null || type.isBlank() ? "OTHER" : type.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalizedType)) throw new IncidentValidationException("Tipo de ocorrência inválido.");
        IncidentReport report = incidents.saveIncident(new IncidentReport(
                contract, actor, normalizedType, description.trim()));
        incidents.record(actor, "INCIDENT_CREATED", "IncidentReport", report.getId(), normalizedType);
        incidents.notifyRole(Role.AGENT, "NEW_INCIDENT",
                "Nova ocorrência requer acompanhamento.", "IncidentReport", report.getId());
        return report;
    }

    @Transactional(readOnly = true)
    public List<IncidentReport> list(UUID actorId) {
        Account actor = account(actorId);
        return incidents.findAllIncidents().stream()
                .filter(incident -> actor.getRole() == Role.AGENT || isParty(incident.getContract().getHireRequest(), actor))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentReport> listForAgency() {
        return incidents.findAllIncidents();
    }

    @Transactional(readOnly = true)
    public List<IncidentMessageView> messages(UUID incidentId, UUID actorId) {
        IncidentReport incident = incident(incidentId);
        requireCanView(incident, account(actorId));
        return incidents.messages(incidentId).stream()
                .map(message -> new IncidentMessageView(message.getId(), message.getAuthor().getName(),
                        message.getMessage(), message.getAttachment() != null, message.getCreatedAt()))
                .toList();
    }

    @Transactional
    public IncidentReport close(UUID incidentId, UUID actorId) {
        IncidentReport incident = incident(incidentId);
        Account actor = account(actorId);
        incident.close();
        incidents.record(actor, "INCIDENT_CLOSED", "IncidentReport", incidentId, "Ocorrência encerrada");
        return incident;
    }

    @Transactional
    public IncidentStatusResult updateStatus(UUID incidentId, UUID actorId, String status, String decision) {
        IncidentReport incident = incident(incidentId);
        Account actor = account(actorId);
        String normalized = status.toUpperCase(Locale.ROOT);
        try {
            incident.updateStatus(normalized, decision);
        } catch (IllegalArgumentException exception) {
            throw new IncidentValidationException(exception.getMessage());
        }
        incidents.record(actor, "INCIDENT_STATUS_UPDATED", "IncidentReport", incidentId,
                status + " " + decision);
        HireRequest request = incident.getContract().getHireRequest();
        incidents.notify(request.getClient().getId(), "INCIDENT_UPDATED",
                "A agência atualizou uma ocorrência do contrato.", "IncidentReport", incidentId);
        incidents.notify(request.getHousekeeper().getAccount().getId(), "INCIDENT_UPDATED",
                "A agência atualizou uma ocorrência do contrato.", "IncidentReport", incidentId);
        return new IncidentStatusResult(incidentId, incident.getStatus());
    }

    @Transactional
    public IncidentMessage addMessage(UUID incidentId, UUID actorId, String text, byte[] attachment, String contentType) {
        IncidentReport incident = incident(incidentId);
        Account actor = account(actorId);
        requireCanView(incident, actor);
        if (!OPEN_STATUSES.contains(incident.getStatus())) {
            throw new IncidentValidationException("Esta ocorrência está encerrada.");
        }
        IncidentMessage saved = incidents.saveMessage(new IncidentMessage(
                incident, actor, text == null ? "" : text.trim(), attachment, contentType));
        incidents.record(actor, "INCIDENT_MESSAGE_ADDED", "IncidentMessage", saved.getId(), "Resposta adicionada");
        HireRequest request = incident.getContract().getHireRequest();
        if (actor.getRole() == Role.AGENT) {
            incidents.notify(request.getClient().getId(), "INCIDENT_MESSAGE",
                    "A agência respondeu à ocorrência.", "IncidentReport", incidentId);
            incidents.notify(request.getHousekeeper().getAccount().getId(), "INCIDENT_MESSAGE",
                    "A agência respondeu à ocorrência.", "IncidentReport", incidentId);
        } else {
            incidents.notifyRole(Role.AGENT, "INCIDENT_MESSAGE",
                    "Foi adicionada uma resposta a uma ocorrência.", "IncidentReport", incidentId);
            UUID otherParty = actor.getId().equals(request.getClient().getId())
                    ? request.getHousekeeper().getAccount().getId() : request.getClient().getId();
            incidents.notify(otherParty, "INCIDENT_MESSAGE",
                    "A outra parte respondeu à ocorrência.", "IncidentReport", incidentId);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public IncidentAttachment attachment(UUID messageId, UUID actorId) {
        IncidentMessage message = incidents.findMessage(messageId).orElseThrow(IncidentNotFoundException::new);
        requireCanView(message.getIncident(), account(actorId));
        if (message.getAttachment() == null) throw new IncidentNotFoundException();
        return new IncidentAttachment(message.getAttachment(), message.getAttachmentContentType());
    }

    private IncidentReport incident(UUID id) {
        return incidents.findIncident(id).orElseThrow(IncidentNotFoundException::new);
    }

    private Account account(UUID id) {
        return incidents.findAccount(id).orElseThrow(IncidentNotFoundException::new);
    }

    private void requireCanView(IncidentReport incident, Account actor) {
        if (actor.getRole() != Role.AGENT && !isParty(incident.getContract().getHireRequest(), actor)) {
            throw new IncidentForbiddenException("");
        }
    }

    private boolean isParty(HireRequest request, Account actor) {
        return request.getClient().getId().equals(actor.getId())
                || request.getHousekeeper().getAccount().getId().equals(actor.getId());
    }
}
