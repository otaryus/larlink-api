package com.agency.api.application.absence;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbsenceWorkflowUseCaseTest {
    private final UUID clientId = UUID.randomUUID();
    private final UUID workerAccountId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final UUID absenceId = UUID.randomUUID();
    private final AbsenceWorkflowPort port = mock(AbsenceWorkflowPort.class);
    private final Account client = mock(Account.class);
    private final Account workerAccount = mock(Account.class);
    private final Account agent = mock(Account.class);
    private final EmploymentContract contract = mock(EmploymentContract.class);
    private final HireRequest hireRequest = mock(HireRequest.class);
    private final HousekeeperProfile worker = mock(HousekeeperProfile.class);
    private final AbsenceRecord absence = mock(AbsenceRecord.class);
    private final AbsenceWorkflowUseCase useCase = new AbsenceWorkflowUseCase(port);

    @BeforeEach
    void setUp() {
        when(port.findAccount(clientId)).thenReturn(Optional.of(client));
        when(port.findAccount(workerAccountId)).thenReturn(Optional.of(workerAccount));
        when(port.findAccount(agentId)).thenReturn(Optional.of(agent));
        when(client.getId()).thenReturn(clientId);
        when(client.getName()).thenReturn("Cliente");
        when(workerAccount.getId()).thenReturn(workerAccountId);
        when(agent.getId()).thenReturn(agentId);
        when(contract.getId()).thenReturn(contractId);
        when(contract.getHireRequest()).thenReturn(hireRequest);
        when(hireRequest.getClient()).thenReturn(client);
        when(hireRequest.getHousekeeper()).thenReturn(worker);
        when(worker.getAccount()).thenReturn(workerAccount);
        when(worker.getName()).thenReturn("Trabalhadora");
        when(port.findContract(contractId)).thenReturn(Optional.of(contract));
        when(port.findAbsence(absenceId)).thenReturn(Optional.of(absence));
        when(absence.getId()).thenReturn(absenceId);
        when(absence.getContract()).thenReturn(contract);
        when(absence.getReportedBy()).thenReturn(client);
    }

    @Test
    void reportsAbsenceAndNotifiesWorkerAndAgency() {
        when(port.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate date = LocalDate.of(2026, 11, 18);

        AbsenceDetails reported = useCase.report(clientId, contractId, date, new BigDecimal("1.00"), "Aviso");

        assertEquals(date, reported.date());
        assertEquals("PENDENTE_JUSTIFICACAO", reported.status());
        verify(port).record(client, "ABSENCE_REPORTED", "AbsenceRecord", null, date.toString());
        verify(port).notify(workerAccountId, "ABSENCE_REPORTED", "Foi registada uma falta no contrato.", "AbsenceRecord", null);
        verify(port).notifyRole(Role.AGENT, "ABSENCE_REPORTED", "Foi registada uma falta para análise.", "AbsenceRecord", null);
    }

    @Test
    void rejectsAbsenceReportsBySomeoneOtherThanContractClient() {
        UUID anotherClient = UUID.randomUUID();
        Account actor = mock(Account.class);
        when(port.findAccount(anotherClient)).thenReturn(Optional.of(actor));

        assertThrows(AbsenceForbiddenException.class, () -> useCase.report(
                anotherClient, contractId, LocalDate.now(), BigDecimal.ONE, null));
        verify(port, never()).save(any());
    }

    @Test
    void acceptsWorkerJustificationAndAlertsAgency() {
        when(port.findAccount(workerAccountId)).thenReturn(Optional.of(workerAccount));

        AbsenceDetails updated = useCase.submitJustification(absenceId, workerAccountId, "Doença", null, null);

        assertEquals(absenceId, updated.id());
        verify(absence).submitJustification("Doença", null, null);
        verify(port).record(workerAccount, "ABSENCE_JUSTIFICATION_SUBMITTED", "AbsenceRecord",
                absenceId, "Justificação submetida");
        verify(port).notifyRole(Role.AGENT, "ABSENCE_JUSTIFICATION",
                "Uma justificação aguarda análise.", "AbsenceRecord", absenceId);
    }

    @Test
    void onlyContractWorkerMaySubmitJustification() {
        UUID unrelated = UUID.randomUUID();
        Account actor = mock(Account.class);
        when(port.findAccount(unrelated)).thenReturn(Optional.of(actor));

        assertThrows(AbsenceForbiddenException.class,
                () -> useCase.submitJustification(absenceId, unrelated, "Texto", null, null));
        verify(absence, never()).submitJustification(any(), any(), any());
    }

    @Test
    void agencyDecisionNotifiesWorkerAndRecordsDecision() {
        when(absence.getStatus()).thenReturn("JUSTIFICADA");

        AbsenceDetails updated = useCase.decide(absenceId, agentId, "justificada", "Comprovativo válido");

        assertEquals(absenceId, updated.id());
        verify(absence).decide("JUSTIFICADA", "Comprovativo válido", agent);
        verify(port).record(agent, "ABSENCE_DECIDED", "AbsenceRecord",
                absenceId, "justificada Comprovativo válido");
        verify(port).notify(workerAccountId, "ABSENCE_DECISION",
                "A agência analisou a justificação da falta: JUSTIFICADA.", "AbsenceRecord", absenceId);
    }

    @Test
    void missingRecordsAndInvalidDomainTransitionsBecomeApplicationErrors() {
        when(port.findAbsence(absenceId)).thenReturn(Optional.empty());
        assertThrows(AbsenceNotFoundException.class, () -> useCase.decide(absenceId, agentId, "JUSTIFICADA", null));
        when(port.findAbsence(absenceId)).thenReturn(Optional.of(absence));
        doThrow(new IllegalStateException("A falta não está à espera de justificação."))
                .when(absence).submitJustification("Texto", null, null);

        AbsenceInvalidStateException error = assertThrows(AbsenceInvalidStateException.class,
                () -> useCase.submitJustification(absenceId, workerAccountId, "Texto", null, null));
        assertEquals("A falta não está à espera de justificação.", error.getMessage());
    }
}
