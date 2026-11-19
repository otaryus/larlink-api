package com.agency.api.application.incidents;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentWorkflowUseCaseTest {
    private final UUID incidentId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private final IncidentWorkflowPort port = mock(IncidentWorkflowPort.class);
    private final IncidentWorkflowUseCase useCase = new IncidentWorkflowUseCase(port);
    private final IncidentReport incident = mock(IncidentReport.class);
    private final EmploymentContract contract = mock(EmploymentContract.class);
    private final HireRequest request = mock(HireRequest.class);
    private final Account client = mock(Account.class);
    private final Account workerAccount = mock(Account.class);
    private final Account agent = mock(Account.class);
    private final HousekeeperProfile worker = mock(HousekeeperProfile.class);

    @BeforeEach
    void setUp() {
        when(port.findContract(contractId)).thenReturn(Optional.of(contract));
        when(port.findIncident(incidentId)).thenReturn(Optional.of(incident));
        when(port.findAccount(clientId)).thenReturn(Optional.of(client));
        when(port.findAccount(workerId)).thenReturn(Optional.of(workerAccount));
        when(port.findAccount(agentId)).thenReturn(Optional.of(agent));
        when(contract.getId()).thenReturn(contractId);
        when(contract.getHireRequest()).thenReturn(request);
        when(request.getClient()).thenReturn(client);
        when(request.getHousekeeper()).thenReturn(worker);
        when(worker.getAccount()).thenReturn(workerAccount);
        when(client.getId()).thenReturn(clientId);
        when(workerAccount.getId()).thenReturn(workerId);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getRole()).thenReturn(Role.AGENT);
        when(incident.getId()).thenReturn(incidentId);
        when(incident.getContract()).thenReturn(contract);
        when(incident.getStatus()).thenReturn("ABERTA");
        when(port.saveIncident(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(port.saveMessage(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reportValidIncidentByPartyAndNotifyAgency() {
        IncidentReport result = useCase.report(clientId, contractId, "OVERTIME", "Horas não pagas");

        assertEquals("OVERTIME", result.getType());
        assertEquals("ABERTA", result.getStatus());
        verify(port).record(client, "INCIDENT_CREATED", "IncidentReport", result.getId(), "OVERTIME");
        verify(port).notifyRole(Role.AGENT, "NEW_INCIDENT", "Nova ocorrência requer acompanhamento.",
                "IncidentReport", result.getId());
    }

    @Test
    void reportRequiresPartyAndKnownCategory() {
        UUID outsider = UUID.randomUUID();
        Account account = mock(Account.class);
        when(port.findAccount(outsider)).thenReturn(Optional.of(account));
        assertThrows(IncidentForbiddenException.class,
                () -> useCase.report(outsider, contractId, "OTHER", "Evento"));
        assertThrows(IncidentValidationException.class,
                () -> useCase.report(clientId, contractId, "NOT_A_TYPE", "Evento"));
        verify(port, never()).saveIncident(any());
    }

    @Test
    void closeAndStatusDecisionAuditAndNotifyParties() {
        IncidentReport closed = useCase.close(incidentId, agentId);
        assertSame(incident, closed);
        verify(incident).close();
        IncidentStatusResult status = useCase.updateStatus(incidentId, agentId, "RESOLVIDA", "Acordo");
        assertEquals("ABERTA", status.status());
        verify(incident).updateStatus("RESOLVIDA", "Acordo");
        verify(port).notify(clientId, "INCIDENT_UPDATED", "A agência atualizou uma ocorrência do contrato.",
                "IncidentReport", incidentId);
        verify(port).notify(workerId, "INCIDENT_UPDATED", "A agência atualizou uma ocorrência do contrato.",
                "IncidentReport", incidentId);
    }

    @Test
    void permitsPartyMessagesAndNotifiesOtherPartyOrAgency() {
        IncidentMessage message = useCase.addMessage(incidentId, clientId, "  Mais detalhes  ", null, null);

        assertEquals("Mais detalhes", message.getMessage());
        verify(port).record(client, "INCIDENT_MESSAGE_ADDED", "IncidentMessage",
                message.getId(), "Resposta adicionada");
        verify(port).notifyRole(Role.AGENT, "INCIDENT_MESSAGE",
                "Foi adicionada uma resposta a uma ocorrência.", "IncidentReport", incidentId);
        verify(port).notify(workerId, "INCIDENT_MESSAGE", "A outra parte respondeu à ocorrência.",
                "IncidentReport", incidentId);
    }

    @Test
    void agentMessagesNotifyBothPartiesAndClosedIncidentsCannotReceiveMessages() {
        IncidentMessage message = useCase.addMessage(incidentId, agentId, "", new byte[]{1, 2}, "image/png");
        assertEquals("", message.getMessage());
        verify(port).notify(clientId, "INCIDENT_MESSAGE", "A agência respondeu à ocorrência.",
                "IncidentReport", incidentId);
        verify(port).notify(workerId, "INCIDENT_MESSAGE", "A agência respondeu à ocorrência.",
                "IncidentReport", incidentId);
        when(incident.getStatus()).thenReturn("ENCERRADA");
        assertThrows(IncidentValidationException.class,
                () -> useCase.addMessage(incidentId, agentId, "Mensagem", null, null));
    }

    @Test
    void viewsAreScopedAndAttachmentsRequireAccess() {
        when(port.findAllIncidents()).thenReturn(List.of(incident));
        when(port.messages(incidentId)).thenReturn(List.of());
        assertEquals(1, useCase.list(clientId).size());
        assertEquals(List.of(), useCase.messages(incidentId, clientId));
        when(incident.getEvidence()).thenReturn(new byte[]{1});
        IncidentMessage attachment = mock(IncidentMessage.class);
        when(port.findMessage(incidentId)).thenReturn(Optional.of(attachment));
        when(attachment.getIncident()).thenReturn(incident);
        when(attachment.getAttachment()).thenReturn(new byte[]{1});
        when(attachment.getAttachmentContentType()).thenReturn("image/png");
        assertArrayEquals(new byte[]{1}, useCase.attachment(incidentId, agentId).content());
    }
}
