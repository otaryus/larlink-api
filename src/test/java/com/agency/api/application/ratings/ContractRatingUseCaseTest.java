package com.agency.api.application.ratings;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractRatingUseCaseTest {
    private final UUID contractId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final ContractRatingPort port = mock(ContractRatingPort.class);
    private final ContractRatingUseCase useCase = new ContractRatingUseCase(port);
    private final EmploymentContract contract = mock(EmploymentContract.class);
    private final HireRequest request = mock(HireRequest.class);
    private final Account client = mock(Account.class);
    private final Account workerAccount = mock(Account.class);
    private final HousekeeperProfile worker = mock(HousekeeperProfile.class);

    @BeforeEach
    void setup() {
        when(port.contract(contractId)).thenReturn(Optional.of(contract));
        when(contract.getId()).thenReturn(contractId);
        when(contract.getHireRequest()).thenReturn(request);
        when(request.getClient()).thenReturn(client);
        when(request.getHousekeeper()).thenReturn(worker);
        when(worker.getAccount()).thenReturn(workerAccount);
        when(client.getId()).thenReturn(clientId);
        when(workerAccount.getId()).thenReturn(workerId);
        when(client.getName()).thenReturn("Client");
        when(port.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listsRatingsWithEligibilityAverageAndCallerFlag() {
        when(port.paid(contractId)).thenReturn(true);
        ContractRating first = mock(ContractRating.class);
        ContractRating second = mock(ContractRating.class);
        when(first.getScore()).thenReturn(4);
        when(first.getAuthor()).thenReturn(client);
        when(second.getScore()).thenReturn(2);
        when(port.ratings(contractId)).thenReturn(List.of(first, second));

        ContractRatingDetails result = useCase.list(contractId, clientId);

        assertTrue(result.eligible());
        assertEquals(3.0, result.average());
        assertTrue(result.submittedByMe());
    }

    @Test
    void createsRatingOnlyForEligiblePartyOnceAndNotifiesOtherParty() {
        when(port.paid(contractId)).thenReturn(true);
        when(port.alreadyRated(contractId, clientId)).thenReturn(false);
        when(port.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ContractRating result = useCase.create(contractId, client, 5, "  Great work ");

        assertEquals("Great work", result.getComment());
        verify(port).record(client, "CONTRACT_RATED", "ContractRating",
                result.getId(), "Classificação 5/5");
        verify(port).notify(workerId, "CONTRACT_RATING",
                "Recebeu uma avaliação sobre um contrato concluído.", "ContractRating", result.getId());

        when(port.alreadyRated(contractId, clientId)).thenReturn(true);
        assertThrows(ContractRatingValidationException.class, () -> useCase.create(contractId, client, 5, null));
    }

    @Test
    void rejectsNonPartyAndIneligibleContract() {
        UUID outsiderId = UUID.randomUUID();
        assertThrows(ContractRatingForbiddenException.class, () -> useCase.list(contractId, outsiderId));
        when(port.paid(contractId)).thenReturn(false);
        when(contract.getStatus()).thenReturn("ACTIVE");
        assertThrows(ContractRatingValidationException.class, () -> useCase.create(contractId, client, 3, null));
    }
}
