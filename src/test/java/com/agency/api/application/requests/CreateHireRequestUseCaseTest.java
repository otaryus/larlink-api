package com.agency.api.application.requests;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreateHireRequestUseCaseTest {
    private final UUID clientId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final Account client = mock(Account.class);
    private final Account workerAccount = mock(Account.class);
    private final HousekeeperProfile worker = mock(HousekeeperProfile.class);
    private final AgencyTask task = mock(AgencyTask.class);
    private final RequestCreationPort port = mock(RequestCreationPort.class);
    private final CreateHireRequestUseCase useCase = new CreateHireRequestUseCase(port);

    @BeforeEach
    void setUp() {
        when(port.findAccount(clientId)).thenReturn(Optional.of(client));
        when(port.hasClientProfile(clientId)).thenReturn(true);
        when(port.findWorker(workerId)).thenReturn(Optional.of(worker));
        when(client.getId()).thenReturn(clientId);
        when(worker.getId()).thenReturn(workerId);
        when(worker.getAccount()).thenReturn(workerAccount);
        when(workerAccount.getId()).thenReturn(UUID.randomUUID());
        when(worker.getStatus()).thenReturn("APPROVED");
        when(worker.getProvince()).thenReturn("Cidade de Maputo");
        when(worker.getDistrict()).thenReturn("KaMpfumu");
        when(worker.getSkills()).thenReturn(Set.of("CLEANING"));
        when(worker.getAvailability()).thenReturn(Set.of("MONDAY"));
        when(worker.getExpectedSalary()).thenReturn(10_000);
        when(port.isValidLocation("Cidade de Maputo", "KaMpfumu")).thenReturn(true);
        when(port.canonicalArea("Cidade de Maputo")).thenReturn("Cidade de Maputo");
        when(port.canonicalArea(worker.getProvince())).thenReturn("Cidade de Maputo");
        when(port.coordinates("Cidade de Maputo", "KaMpfumu")).thenReturn(Optional.empty());
        when(port.activeTasks(Set.of())).thenReturn(Set.of());
        when(port.isWorkerEmployed(workerId)).thenReturn(false);
        when(port.hasOpenRequest(clientId, workerId)).thenReturn(false);
        when(port.requestValidityDays()).thenReturn(14);
        when(port.save(any(HireRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsRequestWithDerivedSkillsScopeAndInitialProposalWhenOfferIsLow() {
        CreateHireRequestCommand command = command(9_000, Set.of("CLEANING"), Set.of());

        HireRequest request = useCase.create(clientId, command);

        assertEquals("EM_NEGOCIACAO", request.getStatus());
        assertEquals(Set.of("CLEANING"), request.getRequiredSkills());
        assertEquals("Rotina da casa", request.getTaskScope());
        assertEquals("KaMpfumu", request.getDistrict());
        assertEquals("09:00", request.getStartTime());
        verify(port).saveInitialProposal(request, client, 9_000);
        verify(port).record(eq(client), eq("HIRE_REQUEST_CREATED"), eq("HireRequest"),
                eq(request.getId()), eq("Oferta inicial: 9000"));
        verify(port).notify(eq(workerAccount.getId()), eq("NEW_HIRE_REQUEST"), anyString(),
                eq("HireRequest"), eq(request.getId()));
    }

    @Test
    void rejectsInvalidScheduleBeforeAnyPersistence() {
        CreateHireRequestCommand command = new CreateHireRequestCommand(workerId, Set.of("CLEANING"),
                Set.of(), Set.of("MONDAY"), "Cidade de Maputo", "KaMpfumu", "",
                "09:00", "12:00", LocalDate.now().plusDays(1), "Scope", 10_000,
                6, 8, "");

        RequestValidationException error = assertThrows(RequestValidationException.class,
                () -> useCase.create(clientId, command));

        assertEquals("O horário de saída deve ser posterior à entrada e corresponder a horas completas.", error.getMessage());
        verify(port, never()).save(any());
    }

    @Test
    void rejectsUnapprovedCandidateBeforeCreatingRequest() {
        when(worker.getStatus()).thenReturn("PENDING");

        assertEquals("Empregada indisponível.",
                assertThrows(RequestValidationException.class, () -> useCase.create(clientId, command(
                        10_000, Set.of("CLEANING"), Set.of()))).getMessage());
        verify(port, never()).save(any());
    }

    @Test
    void rejectsDuplicateRequestWithConflict() {
        when(port.hasOpenRequest(clientId, workerId)).thenReturn(true);

        RequestConflictException error = assertThrows(RequestConflictException.class,
                () -> useCase.create(clientId, command(10_000, Set.of("CLEANING"), Set.of())));

        assertEquals("Já existe um pedido pendente para este perfil.", error.getMessage());
        verify(port, never()).save(any());
    }

    @Test
    void appendsTaskSkillsAndNamesAndRejectsUnavailableTasks() {
        UUID taskId = UUID.randomUUID();
        when(task.isActive()).thenReturn(true);
        when(task.getSkillCode()).thenReturn("COOKING");
        when(task.getName()).thenReturn("Cozinhar");
        when(port.activeTasks(Set.of(taskId))).thenReturn(Set.of(task));
        when(worker.getSkills()).thenReturn(Set.of("CLEANING", "COOKING"));

        HireRequest request = useCase.create(clientId,
                command(10_000, Set.of("CLEANING"), Set.of(taskId)));

        assertEquals(Set.of("CLEANING", "COOKING"), request.getRequiredSkills());
        assertEquals("Rotina da casa\nTarefas seleccionadas no catálogo: Cozinhar", request.getTaskScope());
        assertEquals(Set.of(task), request.getSelectedTasks());
        when(port.activeTasks(Set.of(taskId))).thenReturn(Set.of());
        assertEquals("Uma tarefa seleccionada já não está disponível.",
                assertThrows(RequestValidationException.class, () -> useCase.create(clientId,
                        command(10_000, Set.of("CLEANING"), Set.of(taskId)))).getMessage());
    }

    private CreateHireRequestCommand command(int salary, Set<String> skills, Set<UUID> taskIds) {
        return new CreateHireRequestCommand(workerId, skills, taskIds, Set.of("MONDAY"),
                "Cidade de Maputo", "KaMpfumu", "Polana", "09:00", "17:00",
                LocalDate.now().plusDays(1), "Rotina da casa", salary, 6, 8, null);
    }
}
