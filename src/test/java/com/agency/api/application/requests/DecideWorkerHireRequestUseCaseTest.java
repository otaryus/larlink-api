package com.agency.api.application.requests;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DecideWorkerHireRequestUseCaseTest {
    private final UUID requestId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final HireRequest request = mock(HireRequest.class);
    private final Account workerAccount = mock(Account.class);
    private final Account actorAccount = mock(Account.class);
    private final Account client = mock(Account.class);
    private final HousekeeperProfile worker = mock(HousekeeperProfile.class);
    private final RequestDecisionPort port = mock(RequestDecisionPort.class);
    private final DecideWorkerHireRequestUseCase useCase = new DecideWorkerHireRequestUseCase(port);

    @BeforeEach
    void setUp() {
        when(port.lockRequest(requestId)).thenReturn(Optional.of(request));
        when(port.findAccount(workerId)).thenReturn(Optional.of(actorAccount));
        when(workerAccount.getId()).thenReturn(workerId);
        when(actorAccount.getId()).thenReturn(workerId);
        when(request.getStatus()).thenReturn("APPROVED");
        when(request.getHousekeeper()).thenReturn(worker);
        when(worker.getAccount()).thenReturn(workerAccount);
        when(request.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(UUID.randomUUID());
        when(port.proposals(requestId)).thenReturn(List.of());
        when(port.hasContract(requestId)).thenReturn(false);
        when(port.generateDraft(request)).thenReturn(new ContractDraft("Draft", false));
        when(port.saveContract(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptanceGeneratesContractAndDeclineNotifiesClient() {
        WorkerRequestDecisionResult accepted = useCase.decide(requestId, workerId, true);

        assertNotNull(accepted.contract());
        verify(request).decide("WORKER_ACCEPTED", null);
        verify(port).ensureInitialContractVersion(accepted.contract());
        when(request.getStatus()).thenReturn("PENDING");
        WorkerRequestDecisionResult declined = useCase.decide(requestId, workerId, false);
        assertNull(declined.contract());
        verify(request).decide("WORKER_DECLINED", null);
        verify(port).notify(eq(client.getId()), eq("PROPOSAL_REJECTED"), anyString(),
                eq("HireRequest"), eq(requestId));
    }

    @Test
    void rejectsWrongWorkerAndDuplicateContract() {
        UUID unrelated = UUID.randomUUID();
        Account unrelatedAccount = mock(Account.class);
        when(unrelatedAccount.getId()).thenReturn(unrelated);
        when(port.findAccount(unrelated)).thenReturn(Optional.of(unrelatedAccount));
        assertThrows(RequestForbiddenException.class, () -> useCase.decide(requestId, unrelated, true));
        when(port.hasContract(requestId)).thenReturn(true);
        assertThrows(RequestConflictException.class, () -> useCase.decide(requestId, workerId, true));
        verify(port, never()).generateDraft(any());
    }
}
