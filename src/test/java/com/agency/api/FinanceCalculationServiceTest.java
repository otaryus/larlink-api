package com.agency.api;

import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.FinanceCalculationService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceCalculationServiceTest {

    private record Scenario(FinanceCalculationService.Statement statement) {}

    private Scenario calculate(String decision) {
        var client = new Account("Cliente Exemplo", "client@example.test", "hash", Role.CLIENT);
        var workerAccount = new Account("Trabalhadora Exemplo", "worker@example.test", "hash", Role.CANDIDATE);
        var worker = new HousekeeperProfile(workerAccount, LocalDate.of(1990, 1, 1), "840000000", "Maputo", "Centro", java.util.Set.of("CLEANING"), java.util.Set.of("MON"), 10000, 5);
        var request = new HireRequest(client, worker, java.util.Set.of("CLEANING"), java.util.Set.of("MON"), LocalDate.of(2026, 1, 1), "", "Limpeza", 10000, 12, 8);
        var contract = new EmploymentContract(request, "Contrato", false);
        var absence = new AbsenceRecord(contract, client, LocalDate.of(2026, 2, 4), BigDecimal.ONE, "Ausência");
        if ("SEM_JUSTIFICACAO".equals(decision)) {
            absence.declareNoJustification();
        } else if (!"PENDENTE_JUSTIFICACAO".equals(decision)) {
            absence.submitJustification("Comprovativo", null, null);
            absence.decide(decision, "Decisão", client);
        }
        var absences = mock(AbsenceRepository.class);
        when(absences.findAll()).thenReturn(List.of(absence));
        var additional = mock(AdditionalWorkRepository.class);
        when(additional.findAll()).thenReturn(List.of());
        var policies = mock(AgencyFinancePolicyRepository.class);
        when(policies.findById("agency")).thenReturn(Optional.empty());
        return new Scenario(new FinanceCalculationService(absences, additional, policies, 30).calculate(contract, "2026-02"));
    }

    @Test
    void justifiedAbsenceDiscountsClientAndIsCompensatedByAgency() {
        var s = calculate("JUSTIFICADA").statement();
        assertEquals(new BigDecimal("333.33"), s.clientAbsenceDiscount());
        assertEquals(new BigDecimal("0.00"), s.workerAbsenceDiscount());
        assertEquals(new BigDecimal("333.33"), s.agencyCompensation());
        assertEquals(new BigDecimal("10666.67"), s.clientDue());
        assertEquals(new BigDecimal("1000.00"), s.agencyFee());
        assertEquals(new BigDecimal("10000.00"), s.workerNet());
    }

    @Test
    void unexcusedAbsenceDiscountsBothClientAndWorker() {
        var s = calculate("NAO_JUSTIFICADA").statement();
        assertEquals(new BigDecimal("333.33"), s.clientAbsenceDiscount());
        assertEquals(new BigDecimal("333.33"), s.workerAbsenceDiscount());
        assertEquals(BigDecimal.ZERO.setScale(2), s.agencyCompensation());
        assertEquals(new BigDecimal("10666.67"), s.clientDue());
        assertEquals(new BigDecimal("1000.00"), s.agencyFee());
        assertEquals(new BigDecimal("9666.67"), s.workerNet());
    }

    @Test
    void pendingAbsenceDoesNotChangeTheEmployerOrWorkerSettlement() {
        var s = calculate("PENDENTE_JUSTIFICACAO").statement();
        assertEquals(new BigDecimal("0.00"), s.clientAbsenceDiscount());
        assertEquals(new BigDecimal("0.00"), s.workerAbsenceDiscount());
        assertEquals(new BigDecimal("0.00"), s.agencyCompensation());
        assertEquals(new BigDecimal("11000.00"), s.clientDue());
        assertEquals(new BigDecimal("10000.00"), s.workerNet());
    }

    @Test
    void absenceWithoutJustificationDoesNotAffectSettlementUntilAgencyDecision() {
        var s = calculate("SEM_JUSTIFICACAO").statement();
        assertEquals(new BigDecimal("0.00"), s.clientAbsenceDiscount());
        assertEquals(new BigDecimal("0.00"), s.workerAbsenceDiscount());
        assertEquals(new BigDecimal("0.00"), s.agencyCompensation());
        assertEquals(new BigDecimal("11000.00"), s.clientDue());
        assertEquals(new BigDecimal("10000.00"), s.workerNet());
    }

    @Test
    void approvedAdditionalWorkIsDetailedInStatementAndIncludedInTotals() {
        var client = new Account("Cliente Serviço", "client-extra@example.test", "hash", Role.CLIENT);
        var workerAccount = new Account("Trabalhadora Serviço", "worker-extra@example.test", "hash", Role.CANDIDATE);
        var worker = new HousekeeperProfile(workerAccount, LocalDate.of(1990, 1, 1), "840000000", "Maputo", "Centro",
                java.util.Set.of("CLEANING"), java.util.Set.of("MON"), 10000, 5);
        var request = new HireRequest(client, worker, java.util.Set.of("CLEANING"), java.util.Set.of("MON"),
                LocalDate.of(2026, 1, 1), "", "Limpeza", 10000, 12, 8);
        var contract = new EmploymentContract(request, "Contrato", false);
        var extra = new AdditionalWork(contract, client, "Limpeza profunda", LocalDate.of(2026, 2, 12),
                new BigDecimal("2"), new BigDecimal("750"), "ADDITIONAL_SERVICE");
        extra.decide("APPROVED", client, "Aprovado");
        var absences = mock(AbsenceRepository.class);
        when(absences.findAll()).thenReturn(List.of());
        var additional = mock(AdditionalWorkRepository.class);
        when(additional.findAll()).thenReturn(List.of(extra));
        var policies = mock(AgencyFinancePolicyRepository.class);
        when(policies.findById("agency")).thenReturn(Optional.empty());

        var statement = new FinanceCalculationService(absences, additional, policies, 30)
                .calculate(contract, "2026-02");

        assertEquals(new BigDecimal("1500.00"), statement.additionalServices());
        assertEquals(new BigDecimal("12500.00"), statement.clientDue());
        assertEquals(1, statement.additionalWorkItems().size());
        assertEquals("Limpeza profunda", statement.additionalWorkItems().getFirst().description());
        assertEquals(new BigDecimal("2"), statement.additionalWorkItems().getFirst().quantity());
        assertEquals(new BigDecimal("750"), statement.additionalWorkItems().getFirst().unitPrice());
        assertEquals(new BigDecimal("1500.00"), statement.additionalWorkItems().getFirst().total());
    }

    @Test
    void configuredDivisorAndAgencyFeeAreAppliedWithoutNegativeClientDebt() {
        var client = new Account("Cliente Exemplo", "client-policy@example.test", "hash", Role.CLIENT);
        var workerAccount = new Account("Trabalhadora Exemplo", "worker-policy@example.test", "hash", Role.CANDIDATE);
        var worker = new HousekeeperProfile(workerAccount, LocalDate.of(1990, 1, 1), "840000000", "Maputo", "Centro", java.util.Set.of("CLEANING"), java.util.Set.of("MON"), 10000, 5);
        var request = new HireRequest(client, worker, java.util.Set.of("CLEANING"), java.util.Set.of("MON"), LocalDate.of(2026, 1, 1), "", "Limpeza", 10000, 12, 8);
        var contract = new EmploymentContract(request, "Contrato", false);
        var absence = new AbsenceRecord(contract, client, LocalDate.of(2026, 2, 4), new BigDecimal("40"), "Ausência longa");
        absence.submitJustification("Sem comprovativo", null, null);
        absence.decide("JUSTIFICADA", "Decisão", client);
        var absences = mock(AbsenceRepository.class); when(absences.findAll()).thenReturn(List.of(absence));
        var additional = mock(AdditionalWorkRepository.class); when(additional.findAll()).thenReturn(List.of());
        var policy = new AgencyFinancePolicy(); policy.update(20, BigDecimal.ZERO, BigDecimal.ZERO, 5, 14, new BigDecimal("12.50"));
        var policies = mock(AgencyFinancePolicyRepository.class); when(policies.findById("agency")).thenReturn(Optional.of(policy));

        var statement = new FinanceCalculationService(absences, additional, policies, 30).calculate(contract, "2026-02");

        assertEquals(20, statement.divisor());
        assertEquals(new BigDecimal("1250.00"), statement.agencyFee());
        assertEquals(new BigDecimal("0.00"), statement.clientDue());
        assertEquals(new BigDecimal("20000.00"), statement.agencyCompensation());
    }
}
