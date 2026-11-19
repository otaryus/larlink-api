package com.agency.api.application.finance;

import com.agency.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceOperationsUseCaseTest {
    private final UUID contractId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private final UUID obligationId = UUID.randomUUID();
    private final FinanceOperationsPort port = mock(FinanceOperationsPort.class);
    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
    private final FinanceOperationsUseCase useCase = new FinanceOperationsUseCase(port, gateway, true);
    private final EmploymentContract contract = mock(EmploymentContract.class);
    private final HireRequest request = mock(HireRequest.class);
    private final Account client = mock(Account.class);
    private final Account worker = mock(Account.class);
    private final Account workerAccount = mock(Account.class);
    private final Account agent = mock(Account.class);
    private final HousekeeperProfile workerProfile = mock(HousekeeperProfile.class);

    @BeforeEach
    void setUp() {
        when(port.findContract(contractId)).thenReturn(Optional.of(contract));
        when(port.lockContract(contractId)).thenReturn(Optional.of(contract));
        when(port.findAccount(clientId)).thenReturn(Optional.of(client));
        when(port.findAccount(workerId)).thenReturn(Optional.of(workerAccount));
        when(port.findAccount(agentId)).thenReturn(Optional.of(agent));
        when(contract.getId()).thenReturn(contractId);
        when(contract.getStatus()).thenReturn("ACTIVE");
        when(contract.getHireRequest()).thenReturn(request);
        when(request.getClient()).thenReturn(client);
        when(request.getHousekeeper()).thenReturn(workerProfile);
        when(workerProfile.getAccount()).thenReturn(workerAccount);
        when(client.getId()).thenReturn(clientId);
        when(client.getRole()).thenReturn(Role.CLIENT);
        when(workerAccount.getId()).thenReturn(workerId);
        when(workerAccount.getRole()).thenReturn(Role.HOUSEKEEPER);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getRole()).thenReturn(Role.AGENT);
        when(port.saveAdditionalWork(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(port.savePayment(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(port.paymentDueDay()).thenReturn(5);
        when(port.statement(contractId, "2026-10")).thenReturn(Map.of("clientDue", new BigDecimal("1000")));
    }

    @Test
    void createsAdditionalWorkOnlyForContractPartiesAndNotifiesPartiesAndAgency() {
        AdditionalWork created = useCase.createAdditionalWork(clientId, contractId, "  Extra cleaning ",
                LocalDate.of(2026, 10, 4), BigDecimal.valueOf(2), BigDecimal.valueOf(150), "overtime");

        assertEquals("Extra cleaning", created.getDescription());
        assertEquals("OVERTIME", created.getWorkType());
        verify(port).notify(clientId, "ADDITIONAL_WORK",
                "Foi registado um serviço adicional para aprovação.", "AdditionalWork", created.getId());
        verify(port).notify(workerId, "ADDITIONAL_WORK",
                "Foi registado um serviço adicional para aprovação.", "AdditionalWork", created.getId());
        verify(port).notifyRole(Role.AGENT, "ADDITIONAL_WORK",
                "Um serviço adicional aguarda validação.", "AdditionalWork", created.getId());
    }

    @Test
    void rejectsNonPartyAndUnsupportedAdditionalWorkType() {
        UUID outsiderId = UUID.randomUUID();
        Account outsider = mock(Account.class);
        when(outsider.getId()).thenReturn(outsiderId);
        when(outsider.getRole()).thenReturn(Role.CLIENT);
        when(port.findAccount(outsiderId)).thenReturn(Optional.of(outsider));
        assertThrows(FinanceForbiddenException.class, () -> useCase.createAdditionalWork(
                outsiderId, contractId, "Work", LocalDate.now(), BigDecimal.ONE, BigDecimal.TEN, "OTHER"));
        assertThrows(FinanceValidationException.class, () -> useCase.createAdditionalWork(
                clientId, contractId, "Work", LocalDate.now(), BigDecimal.ONE, BigDecimal.TEN, "OTHER"));
        verify(port, never()).saveAdditionalWork(any());
    }

    @Test
    void agencyDecisionAppliesConfiguredRateWhenNoPriceWasProvided() {
        UUID workId = UUID.randomUUID();
        AdditionalWork item = mock(AdditionalWork.class);
        when(item.getId()).thenReturn(workId);
        when(port.additionalWork(workId)).thenReturn(Optional.of(item));
        when(item.getWorkType()).thenReturn("OVERTIME");
        when(port.configuredUnitPrice("OVERTIME")).thenReturn(BigDecimal.valueOf(200));

        useCase.decideAdditionalWork(workId, agentId, "APPROVED", null, null);

        verify(item).decide("APPROVED", agent, null, BigDecimal.valueOf(200));
        verify(port).record(agent, "ADDITIONAL_WORK_APPROVED", "AdditionalWork", item.getId(), null);
    }

    @Test
    void createsPaymentObligationIdempotentlyWithDueDateAndValidatesContract() {
        when(port.payment(contractId, "2026-10")).thenReturn(Optional.empty());
        var result = useCase.createObligation(contractId, clientId, "2026-10");

        assertTrue(result.created());
        assertEquals(new BigDecimal("1000"), result.obligation().getAmount());
        assertEquals(LocalDate.of(2026, 11, 5), result.obligation().getDueDate());
        verify(port).record(client, "PAYMENT_OBLIGATION_CREATED", "PaymentObligation",
                result.obligation().getId(), "2026-10=1000");

        PaymentObligation existing = mock(PaymentObligation.class);
        when(port.payment(contractId, "2026-10")).thenReturn(Optional.of(existing));
        var repeated = useCase.createObligation(contractId, clientId, "2026-10");
        assertFalse(repeated.created());
        assertSame(existing, repeated.obligation());
    }

    @Test
    void restrictsClientToOwnContractAndRejectsBadPeriod() {
        UUID otherClientId = UUID.randomUUID();
        Account other = mock(Account.class);
        when(other.getId()).thenReturn(otherClientId);
        when(other.getRole()).thenReturn(Role.CLIENT);
        when(port.findAccount(otherClientId)).thenReturn(Optional.of(other));
        assertThrows(FinanceForbiddenException.class,
                () -> useCase.createObligation(contractId, otherClientId, "2026-10"));
        assertThrows(FinanceValidationException.class,
                () -> useCase.createObligation(contractId, clientId, "bad"));
        when(contract.getStatus()).thenReturn("CLOSED");
        assertThrows(FinanceValidationException.class,
                () -> useCase.createObligation(contractId, clientId, "2026-10"));
    }

    @Test
    void verifiesProviderCallbacksAndHandlesDuplicateTransactions() throws Exception {
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.verifyCallback("body", "sig")).thenReturn(true);
        when(gateway.parseCallback("body")).thenReturn(
                new PaymentGatewayPort.Callback(obligationId, "tx-1", new BigDecimal("1000")));
        PaymentObligation obligation = mock(PaymentObligation.class);
        when(port.payment(obligationId)).thenReturn(Optional.of(obligation));
        when(obligation.getId()).thenReturn(obligationId);
        when(obligation.getReferenceMonth()).thenReturn("2026-10");
        when(obligation.getProvider()).thenReturn("provider");
        when(port.paymentByTransaction("tx-1")).thenReturn(Optional.empty());
        when(obligation.getContract()).thenReturn(contract);
        assertFalse(useCase.handleCallback("provider", "body", "sig").duplicate());
        verify(obligation).confirmPaid("tx-1", new BigDecimal("1000"));
        verify(port, times(2)).notify(any(), eq("PAYMENT_CONFIRMED"), anyString(),
                eq("PaymentObligation"), eq(obligationId));
        verify(port).notifyRole(Role.AGENT, "PAYMENT_CONFIRMED",
                "Pagamento confirmado pelo prestador.", "PaymentObligation", obligationId);

        when(port.paymentByTransaction("tx-1")).thenReturn(Optional.of(obligation));
        assertTrue(useCase.handleCallback("provider", "body", "sig").duplicate());
        verify(obligation, times(1)).confirmPaid(anyString(), any());
    }

    @Test
    void exposesClosedStatementAndGatesSimulatedPayments() {
        when(port.statement(contractId, "2026-10")).thenReturn(Map.of("closed", true));
        assertEquals(Map.of("closed", true), useCase.statement(contractId, clientId, "2026-10"));
        when(port.payment(obligationId)).thenReturn(Optional.of(mock(PaymentObligation.class)));
        var simulated = useCase.simulatePayment(obligationId, agentId);
        assertNotNull(simulated);
    }

    @Test
    void issuesProviderReferenceOnlyForConfiguredProviderAndAuditRole() {
        PaymentObligation obligation = mock(PaymentObligation.class);
        when(port.payment(obligationId)).thenReturn(Optional.of(obligation));
        when(obligation.getStatus()).thenReturn("PENDENTE");
        when(obligation.getId()).thenReturn(obligationId);
        when(obligation.getAmount()).thenReturn(BigDecimal.valueOf(1000));
        when(obligation.getReferenceMonth()).thenReturn("2026-10");
        when(obligation.getContract()).thenReturn(contract);
        when(client.getName()).thenReturn("Client");
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.issue(any())).thenReturn(
                new PaymentGatewayPort.IssuedReference("mobile-money", "123", "456"));

        assertSame(obligation, useCase.issueReference(obligationId, agentId));

        verify(obligation).assignProviderReference("mobile-money", "123", "456");
        verify(port).record(agent, "PAYMENT_REFERENCE_ISSUED", "PaymentObligation",
                obligationId, "mobile-money");
        when(gateway.isConfigured()).thenReturn(false);
        assertThrows(PaymentProviderUnavailableException.class,
                () -> useCase.issueReference(obligationId, agentId));
    }

    @Test
    void statementAndAdjustmentEnforceAccessAndClosedPeriodRequirement() {
        UUID outsiderId = UUID.randomUUID();
        Account outsider = mock(Account.class);
        when(outsider.getId()).thenReturn(outsiderId);
        when(outsider.getRole()).thenReturn(Role.CLIENT);
        when(port.findAccount(outsiderId)).thenReturn(Optional.of(outsider));
        assertThrows(FinanceForbiddenException.class,
                () -> useCase.statement(contractId, outsiderId, "2026-10"));
        assertThrows(FinanceValidationException.class,
                () -> useCase.statement(contractId, clientId, "bad"));

        FinanceAdjustment adjustment = mock(FinanceAdjustment.class);
        when(port.createAdjustment(eq(contractId), eq("2026-10"), eq(agentId), any(),
                eq("reason"), eq("AdjustmentInput[amount=1, reason=reason]"))).thenReturn(adjustment);
        assertSame(adjustment, useCase.addAdjustment(contractId, "2026-10", agentId,
                BigDecimal.ONE, " reason ", "AdjustmentInput[amount=1, reason=reason]"));

        when(port.createAdjustment(eq(contractId), eq("2026-10"), eq(agentId), any(),
                anyString(), anyString())).thenThrow(new FinancePeriodCloseException(new IllegalStateException()));
        assertThrows(FinanceValidationException.class, () -> useCase.addAdjustment(contractId,
                "2026-10", agentId, BigDecimal.ONE, "reason", "details"));
    }

    @Test
    void rejectsUnauthenticatedOrMismatchedProviderCallbacks() throws Exception {
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.verifyCallback(anyString(), any())).thenReturn(false);
        assertThrows(PaymentCallbackAuthenticationException.class,
                () -> useCase.handleCallback("provider", "body", "bad"));

        when(gateway.verifyCallback("body", "sig")).thenReturn(true);
        when(gateway.parseCallback("body")).thenReturn(
                new PaymentGatewayPort.Callback(obligationId, "tx", BigDecimal.ONE));
        PaymentObligation obligation = mock(PaymentObligation.class);
        when(port.paymentByTransaction("tx")).thenReturn(Optional.empty());
        when(port.payment(obligationId)).thenReturn(Optional.of(obligation));
        when(obligation.getProvider()).thenReturn("different-provider");
        assertThrows(FinanceValidationException.class,
                () -> useCase.handleCallback("provider", "body", "sig"));
    }
}
