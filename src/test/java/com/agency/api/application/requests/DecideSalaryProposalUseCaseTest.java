package com.agency.api.application.requests;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DecideSalaryProposalUseCaseTest {
    private final UUID requestId = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final HireRequest request = mock(HireRequest.class);
    private final SalaryProposal proposal = mock(SalaryProposal.class);
    private final Account actor = mock(Account.class);
    private final Account author = mock(Account.class);
    private final HousekeeperProfile worker = mock(HousekeeperProfile.class);
    private final RequestDecisionPort port = mock(RequestDecisionPort.class);
    private final DecideSalaryProposalUseCase useCase = new DecideSalaryProposalUseCase(port);

    @BeforeEach
    void setUp() {
        when(port.lockRequest(requestId)).thenReturn(Optional.of(request));
        when(port.findProposal(proposalId)).thenReturn(Optional.of(proposal));
        when(port.findAccount(actorId)).thenReturn(Optional.of(actor));
        when(request.getId()).thenReturn(requestId);
        when(request.getStatus()).thenReturn("EM_NEGOCIACAO");
        when(request.getClient()).thenReturn(actor);
        when(request.getHousekeeper()).thenReturn(worker);
        when(request.getMonthlySalary()).thenReturn(9_000);
        when(worker.getAccount()).thenReturn(author);
        when(actor.getId()).thenReturn(actorId);
        when(author.getId()).thenReturn(authorId);
        when(proposal.getId()).thenReturn(proposalId);
        when(proposal.getHireRequest()).thenReturn(request);
        when(proposal.getAuthor()).thenReturn(author);
        when(proposal.getMonthlySalary()).thenReturn(new BigDecimal("9000.00"));
        when(proposal.getStatus()).thenReturn("OPEN");
        when(port.proposals(requestId)).thenReturn(List.of(proposal));
        when(port.hasContract(requestId)).thenReturn(false);
    }

    @Test
    void acceptsProposalClosesOtherOffersCreatesContractAndReturnsHttpResponseData() {
        when(port.generateDraft(request)).thenReturn(new ContractDraft("Minuta", true));
        when(port.saveContract(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(request.getStatus()).thenReturn("CONTRATO_GERADO");

        SalaryProposalDecisionResult result = useCase.decide(
                requestId, proposalId, actorId, "ACCEPTED", "Confirmo");

        assertEquals("CONTRATO_GERADO", result.requestStatus());
        assertNotNull(result.contract());
        assertEquals("Minuta", result.contract().getContractText());
        verify(proposal).mark("ACCEPTED");
        verify(request).setMonthlySalary(9_000);
        verify(request).setStatus("CONTRATO_GERADO");
        verify(port).ensureInitialContractVersion(result.contract());
        verify(port).notifyRole(eq(Role.AGENT), eq("CONTRACT_READY"), anyString(), eq("EmploymentContract"),
                eq(result.contract().getId()));
    }

    @Test
    void rejectsProposalWithoutCreatingContractAndNotifiesAuthor() {
        SalaryProposalDecisionResult result = useCase.decide(
                requestId, proposalId, actorId, "REJECTED", "Não aceito");

        assertEquals("EM_NEGOCIACAO", result.requestStatus());
        assertNull(result.contract());
        verify(proposal).mark("REJECTED");
        verify(request).setStatus("EM_NEGOCIACAO");
        verify(port).notify(eq(authorId), eq("SALARY_REJECTED"), anyString(), eq("HireRequest"), eq(requestId));
        verify(port, never()).generateDraft(any());
    }

    @Test
    void rejectsSamePartyAndInvalidOrClosedProposal() {
        when(proposal.getAuthor()).thenReturn(actor);
        assertThrows(RequestForbiddenException.class,
                () -> useCase.decide(requestId, proposalId, actorId, "ACCEPTED", null));
        when(proposal.getAuthor()).thenReturn(author);
        assertThrows(RequestValidationException.class,
                () -> useCase.decide(requestId, proposalId, actorId, "UNKNOWN", null));
        when(proposal.getStatus()).thenReturn("CLOSED");
        assertThrows(RequestConflictException.class,
                () -> useCase.decide(requestId, proposalId, actorId, "ACCEPTED", null));
        verify(port, never()).generateDraft(any());
    }

    @Test
    void rejectsProposalFromAnotherRequestAsNotFound() {
        HireRequest another = mock(HireRequest.class);
        when(another.getId()).thenReturn(UUID.randomUUID());
        when(proposal.getHireRequest()).thenReturn(another);

        assertThrows(RequestNotFoundException.class,
                () -> useCase.decide(requestId, proposalId, actorId, "ACCEPTED", null));
        verify(port, never()).generateDraft(any());
    }

    @Test
    void loadsHistoryForPartiesOrAgencyOnly() {
        when(actor.getRole()).thenReturn(Role.CLIENT);
        assertEquals(List.of(proposal), useCase.history(requestId, actorId));
        when(actor.getId()).thenReturn(UUID.randomUUID());
        assertThrows(RequestForbiddenException.class, () -> useCase.history(requestId, actorId));
        when(actor.getRole()).thenReturn(Role.AGENT);
        assertEquals(List.of(proposal), useCase.history(requestId, actorId));
    }

    @Test
    void createsCounterOfferAndNotifiesOtherParty() {
        when(worker.getExpectedSalary()).thenReturn(10_000);
        when(port.saveProposal(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SalaryProposal created = useCase.counterOffer(requestId, actorId, new BigDecimal("9500"), "Contra proposta");

        assertEquals(new BigDecimal("9500"), created.getMonthlySalary());
        assertEquals("EM_NEGOCIACAO", request.getStatus());
        verify(port).record(actor, "SALARY_PROPOSAL_CREATED", "HireRequest", requestId, "9500");
        verify(port).notify(eq(authorId), eq("SALARY_PROPOSAL"), anyString(),
                eq("HireRequest"), eq(requestId));
    }

    @Test
    void counterOfferRequiresAmountAndNoExistingOfferBySameAuthor() {
        when(worker.getExpectedSalary()).thenReturn(10_000);
        assertThrows(RequestValidationException.class,
                () -> useCase.counterOffer(requestId, actorId, new BigDecimal("9501"), null));
        when(proposal.getAuthor()).thenReturn(actor);
        assertThrows(RequestValidationException.class,
                () -> useCase.counterOffer(requestId, actorId, new BigDecimal("9500"), null));
        verify(port, never()).saveProposal(any());
    }
}
