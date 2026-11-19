package com.agency.api.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStateRegressionTest {

    private Account account(String name, Role role) {
        return new Account(name, name.toLowerCase().replace(' ', '.') + "@test.local", "hash", role);
    }

    private HireRequest request() {
        var client = account("Cliente Teste", Role.CLIENT);
        var workerAccount = account("Candidata Teste", Role.CANDIDATE);
        var worker = new HousekeeperProfile(workerAccount, LocalDate.of(1990, 1, 1), "840000000", "Maputo", "KaMpfumo", "Polana", Set.of("CLEANING"), Set.of("MON"), 10_000, 5);
        return new HireRequest(client, worker, Set.of("CLEANING"), Set.of("MON"), LocalDate.of(2026, 12, 1), "", "Limpeza", 10_000, 12, 8);
    }

    @Test
    void hireRequestExpiresOnlyWhileOpen() {
        var open = request();
        open.setExpiration(Instant.now().minusSeconds(1));
        open.expireIfDue(Instant.now());
        assertEquals("EXPIRED", open.getStatus());

        var accepted = request();
        accepted.setStatus("WORKER_ACCEPTED");
        accepted.setExpiration(Instant.now().minusSeconds(1));
        accepted.expireIfDue(Instant.now());
        assertEquals("WORKER_ACCEPTED", accepted.getStatus());
    }

    @Test
    void salaryProposalCanOnlyBeClosedOnce() {
        var proposal = new SalaryProposal(request(), account("Cliente Teste", Role.CLIENT), new BigDecimal("10050.00"), "Nova proposta");
        proposal.mark("ACCEPTED");
        assertEquals("ACCEPTED", proposal.getStatus());
        assertThrows(IllegalStateException.class, () -> proposal.mark("REJECTED"));
    }

    @Test
    void contractRequiresBothSignaturesAndCannotBeRevisedAfterSigning() {
        var contract = new EmploymentContract(request(), "Minuta", false);
        contract.signClient("Cliente Teste");
        assertEquals("AWAITING_SIGNATURES", contract.getStatus());
        assertEquals("Cliente Teste", contract.getClientSignatureName());
        assertThrows(IllegalStateException.class, () -> contract.signClient("Cliente Teste"));
        assertThrows(IllegalStateException.class, () -> contract.reviseDraft("Outra minuta"));
        contract.signWorker("Candidata Teste");
        assertEquals("ACTIVE", contract.getStatus());
        assertEquals("Candidata Teste", contract.getWorkerSignatureName());
    }

    @Test
    void absenceCanBeDecidedByAgencyBeforeOrAfterWorkerJustification() {
        var reporter = account("Cliente Teste", Role.CLIENT);
        var pendingAbsence = new AbsenceRecord(new EmploymentContract(request(), "Contrato", false), reporter, LocalDate.of(2026, 11, 18), BigDecimal.ONE, "Falta");
        pendingAbsence.decide("JUSTIFICADA", "Aceite sem justificação", reporter);
        assertEquals("JUSTIFICADA", pendingAbsence.getStatus());
        assertThrows(IllegalStateException.class, () -> pendingAbsence.submitJustification("Atestado", new byte[]{1}, "application/pdf"));
        var submittedAbsence = new AbsenceRecord(new EmploymentContract(request(), "Contrato", false), reporter, LocalDate.of(2026, 11, 18), BigDecimal.ONE, "Falta");
        submittedAbsence.submitJustification("Atestado", new byte[]{1}, "application/pdf");
        submittedAbsence.decide("JUSTIFICADA", "Aceite", reporter);
        assertEquals("JUSTIFICADA", submittedAbsence.getStatus());
        assertThrows(IllegalStateException.class, () -> submittedAbsence.submitJustification("Outra", null, null));
        var noJustification = new AbsenceRecord(new EmploymentContract(request(), "Contrato", false), reporter, LocalDate.of(2026, 11, 18), BigDecimal.ONE, "Falta");
        noJustification.declareNoJustification();
        assertEquals("SEM_JUSTIFICACAO", noJustification.getStatus());
        assertThrows(IllegalStateException.class, () -> noJustification.submitJustification("Atestado", null, null));
        noJustification.decide("NAO_JUSTIFICADA", "Sem comprovativo", reporter);
        assertEquals("NAO_JUSTIFICADA", noJustification.getStatus());
    }

    @Test
    void additionalWorkCanOnlyBeDecidedOnceAndRecalculatesApprovedPrice() {
        var actor = account("Gestor Teste", Role.AGENT);
        var additional = new AdditionalWork(new EmploymentContract(request(), "Contrato", false), actor, "Engomadoria extra", LocalDate.of(2026, 11, 18), new BigDecimal("2.5"), new BigDecimal("100.00"));
        additional.decide("APPROVED", actor, "Aprovado", new BigDecimal("120.00"));
        assertEquals(new BigDecimal("300.00"), additional.getTotal());
        assertThrows(IllegalStateException.class, () -> additional.decide("REJECTED", actor, "Duplicado"));
    }

    @Test
    void incidentAndNotificationRejectInvalidRepeatedTransitions() {
        var reporter = account("Cliente Teste", Role.CLIENT);
        var incident = new IncidentReport(new EmploymentContract(request(), "Contrato", false), reporter, "PAYMENT", "Problema com pagamento");
        assertThrows(IllegalArgumentException.class, () -> incident.updateStatus("INVALID", ""));
        incident.updateStatus("EM_ANALISE", "Em tratamento");
        assertEquals("EM_ANALISE", incident.getStatus());

        var notification = new UserNotification(reporter, "EVENT", "Mensagem", "Contract", null);
        notification.markRead();
        var firstRead = notification.getReadAt();
        notification.markRead();
        assertEquals(firstRead, notification.getReadAt());
    }
}
