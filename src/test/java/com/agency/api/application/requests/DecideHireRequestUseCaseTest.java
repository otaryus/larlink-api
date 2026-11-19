package com.agency.api.application.requests;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecideHireRequestUseCaseTest {
    private final UUID requestId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final HireRequest request = mock(HireRequest.class);
    private final Account actor = mock(Account.class);
    private final Account client = mock(Account.class);
    private final HousekeeperProfile worker = mock(HousekeeperProfile.class);
    private final RequestDecisionPort port = mock(RequestDecisionPort.class);
    private final DecideHireRequestUseCase useCase = new DecideHireRequestUseCase(port);

    @BeforeEach
    void setUp() {
        when(port.lockRequest(requestId)).thenReturn(Optional.of(request));
        when(port.findAccount(actorId)).thenReturn(Optional.of(actor));
        when(request.getStatus()).thenReturn("PENDING");
        when(request.getClient()).thenReturn(client);
        when(request.getHousekeeper()).thenReturn(worker);
        when(client.getId()).thenReturn(UUID.randomUUID());
        when(worker.getAccount()).thenReturn(mock(Account.class));
    }

    @Test
    void approvesPendingRequestAndNotifiesBothParties() {
        HireRequest result = useCase.decide(requestId, actorId, "APPROVED", null);

        assertSame(request, result);
        verify(request).decide("APPROVED", null);
        verify(port).record(actor, "HIRE_REQUEST_APPROVED", "HireRequest", requestId, null);
        verify(port).notify(client.getId(), "HIRE_REQUEST_REVIEWED",
                "A agência actualizou o estado do seu pedido.", "HireRequest", requestId);
    }

    @Test
    void requiresMeaningfulReasonForRejectionAndRejectsNonPendingRequest() {
        assertEquals("O motivo deve ter pelo menos 10 caracteres.",
                assertThrows(RequestValidationException.class,
                        () -> useCase.decide(requestId, actorId, "REJECTED", "curto")).getMessage());
        when(request.getStatus()).thenReturn("APPROVED");
        assertThrows(RequestValidationException.class,
                () -> useCase.decide(requestId, actorId, "APPROVED", null));
        verify(request, never()).decide(anyString(), any());
    }
}
