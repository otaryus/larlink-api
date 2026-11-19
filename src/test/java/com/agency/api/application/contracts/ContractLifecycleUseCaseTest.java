package com.agency.api.application.contracts;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractLifecycleUseCaseTest {
    private final UUID contractId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private final EmploymentContract contract = mock(EmploymentContract.class);
    private final HireRequest request = mock(HireRequest.class);
    private final Account client = mock(Account.class);
    private final Account workerAccount = mock(Account.class);
    private final Account agent = mock(Account.class);
    private final HousekeeperProfile worker = mock(HousekeeperProfile.class);
    private final ContractLifecyclePort port = mock(ContractLifecyclePort.class);
    private final ContractLifecycleUseCase useCase = new ContractLifecycleUseCase(port);

    @BeforeEach
    void setUp() {
        when(port.findContract(contractId)).thenReturn(Optional.of(contract));
        when(port.lockContract(contractId)).thenReturn(Optional.of(contract));
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
        when(client.getName()).thenReturn("Cliente");
        when(workerAccount.getName()).thenReturn("Trabalhadora");
        when(request.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(port.currentDate()).thenReturn(LocalDate.of(2026, 1, 2));
    }

    @Test
    void signatureRequiresAgencyReviewExactNameAndReadConfirmation() {
        when(contract.isAgencyReviewed()).thenReturn(false);
        assertEquals("A agência precisa rever a minuta antes das assinaturas.",
                assertThrows(ContractValidationException.class,
                        () -> useCase.sign(contractId, clientId, "Cliente", true)).getMessage());
        when(contract.isAgencyReviewed()).thenReturn(true);
        assertThrows(ContractValidationException.class,
                () -> useCase.sign(contractId, clientId, "Nome diferente", true));
        assertThrows(ContractValidationException.class,
                () -> useCase.sign(contractId, clientId, "Cliente", false));
        verify(contract, never()).signClient(anyString());
    }

    @Test
    void correctPartySignatureIsRecordedAndActivationNotifiesBoth() {
        when(contract.isAgencyReviewed()).thenReturn(true);
        when(contract.isClientSigned()).thenReturn(true);
        when(contract.isWorkerSigned()).thenReturn(true);

        assertSame(contract, useCase.sign(contractId, clientId, "Cliente", true));

        verify(contract).signClient("Cliente");
        verify(port).record(client, "CONTRACT_SIGNED", "EmploymentContract", contractId, "Cliente assinou");
        verify(port).notify(clientId, "CONTRACT_ACTIVE", "Contrato assinado pelas partes.",
                "EmploymentContract", contractId);
        verify(port).notify(workerId, "CONTRACT_ACTIVE", "Contrato assinado pelas partes.",
                "EmploymentContract", contractId);
    }

    @Test
    void confirmsStartOnlyForPartiesAfterStartDateAndUpdatesWorkerRoleOnSecondConfirmation() {
        when(contract.getStatus()).thenReturn("ACTIVE");
        when(contract.isWorkStarted()).thenReturn(false, true);

        assertSame(contract, useCase.confirmStarted(contractId, workerId));

        verify(contract).confirmStartedByWorker();
        verify(workerAccount).markAsHousekeeper();
        verify(worker).decide("EMPLOYED", null);
        verify(port).notifyRole(eq(Role.AGENT), eq("WORK_START_CONFIRMED"), anyString(),
                eq("EmploymentContract"), eq(contractId));
    }

    @Test
    void rejectsFutureStartAndNonPartyConfirmation() {
        when(contract.getStatus()).thenReturn("ACTIVE");
        when(request.getStartDate()).thenReturn(LocalDate.of(2026, 1, 3));
        assertThrows(ContractValidationException.class, () -> useCase.confirmStarted(contractId, clientId));
        when(request.getStartDate()).thenReturn(LocalDate.of(2026, 1, 1));
        UUID stranger = UUID.randomUUID();
        when(port.findAccount(stranger)).thenReturn(Optional.of(mock(Account.class)));
        assertThrows(ContractForbiddenException.class, () -> useCase.confirmStarted(contractId, stranger));
    }

    @Test
    void reviewAndTerminationProduceAuditAndTerminationRestoresAvailability() {
        assertSame(contract, useCase.review(contractId, agentId));
        verify(contract).reviewByAgency();
        when(contract.getStatus()).thenReturn("ACTIVE");
        when(port.hasOtherActiveContract(contractId, worker.getId())).thenReturn(false);

        assertSame(contract, useCase.terminate(contractId, agentId, "Contrato encerrado por acordo"));

        verify(contract).terminate("Contrato encerrado por acordo");
        verify(worker).decide("APPROVED", null);
        verify(port).record(agent, "CONTRACT_TERMINATED", "EmploymentContract",
                contractId, "Contrato encerrado por acordo");
    }

    @Test
    void reviewDecisionPreservesChangeNoteAndNotifiesParties() {
        when(contract.getAgencyReviewState()).thenReturn("CHANGES_REQUESTED");
        when(contract.getAgencyReviewNote()).thenReturn("Actualizar horário");

        ContractReviewResult result = useCase.decideReview(contractId, agentId, false, " Actualizar horário ");

        assertEquals("CHANGES_REQUESTED", result.state());
        assertEquals("Actualizar horário", result.note());
        verify(contract).requestAgencyChanges("Actualizar horário");
        verify(port).notify(clientId, "CONTRACT_REVIEW", "A agência pediu alterações na minuta:  Actualizar horário ",
                "EmploymentContract", contractId);
    }

    @Test
    void contractDocumentsArePartyScopedAndInitialVersionIsEnsured() {
        ContractRevision revision = mock(ContractRevision.class);
        when(port.history(contractId)).thenReturn(List.of(revision));
        assertEquals(List.of(revision), useCase.history(contractId, clientId));
        verify(port).ensureInitialVersion(contract);

        when(port.pdf(contract)).thenReturn(new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, useCase.pdf(contractId, workerId));
        UUID strangerId = UUID.randomUUID();
        Account stranger = mock(Account.class);
        when(stranger.getId()).thenReturn(strangerId);
        when(stranger.getRole()).thenReturn(Role.CLIENT);
        when(port.findAccount(strangerId)).thenReturn(Optional.of(stranger));
        assertThrows(ContractForbiddenException.class, () -> useCase.history(contractId, strangerId));
    }
}
