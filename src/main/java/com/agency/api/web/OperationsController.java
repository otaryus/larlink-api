package com.agency.api.web;

import com.agency.api.domain.*;
import com.agency.api.service.ContractDocumentService;
import com.agency.api.application.profiles.ProfileWorkflowUseCase;
import com.agency.api.application.absence.AbsenceForbiddenException;
import com.agency.api.application.absence.AbsenceDetails;
import com.agency.api.application.absence.AbsenceInvalidStateException;
import com.agency.api.application.absence.AbsenceNotFoundException;
import com.agency.api.application.absence.AbsenceWorkflowUseCase;
import com.agency.api.application.finance.CloseFinancePeriodUseCase;
import com.agency.api.application.finance.ContractNotFoundException;
import com.agency.api.application.finance.FinancePeriodAlreadyClosedException;
import com.agency.api.application.finance.FinancePeriodCloseException;
import com.agency.api.application.finance.FinanceOperationsUseCase;
import com.agency.api.application.finance.FinanceNotFoundException;
import com.agency.api.application.finance.FinanceForbiddenException;
import com.agency.api.application.finance.FinanceValidationException;
import com.agency.api.application.finance.FinanceConflictException;
import com.agency.api.application.finance.PaymentProviderUnavailableException;
import com.agency.api.application.finance.PaymentReferenceException;
import com.agency.api.application.finance.PaymentCallbackAuthenticationException;
import com.agency.api.application.requests.DecideSalaryProposalUseCase;
import com.agency.api.application.requests.RequestConflictException;
import com.agency.api.application.requests.RequestForbiddenException;
import com.agency.api.application.requests.RequestNotFoundException;
import com.agency.api.application.requests.RequestValidationException;
import com.agency.api.application.contracts.ContractLifecycleUseCase;
import com.agency.api.application.contracts.ContractLifecycleNotFoundException;
import com.agency.api.application.contracts.ContractValidationException;
import com.agency.api.application.contracts.ContractForbiddenException;
import com.agency.api.application.incidents.*;
import com.agency.api.application.management.*;
import com.agency.api.application.ratings.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.math.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OperationsController {
    private static final Logger log = LoggerFactory.getLogger(OperationsController.class);
    private final ContractDocumentService documents;
    private final boolean testPaymentsEnabled;
    private final CloseFinancePeriodUseCase closeFinancePeriod;
    private final FinanceOperationsUseCase financeOperations;
    private final AbsenceWorkflowUseCase absenceWorkflow;
    private final DecideSalaryProposalUseCase salaryProposalWorkflow;
    private final ContractLifecycleUseCase contractLifecycle;
    private final IncidentWorkflowUseCase incidentWorkflow;
    private final ManagementUseCase management;
    private final ContractRatingUseCase contractRatings;
    private final ProfileWorkflowUseCase profiles;

    public OperationsController(ContractDocumentService documents,
            CloseFinancePeriodUseCase closeFinancePeriod,
            FinanceOperationsUseCase financeOperations,
            AbsenceWorkflowUseCase absenceWorkflow,
            DecideSalaryProposalUseCase salaryProposalWorkflow,
            ContractLifecycleUseCase contractLifecycle,
            IncidentWorkflowUseCase incidentWorkflow,
            ManagementUseCase management,
            ContractRatingUseCase contractRatings,
            ProfileWorkflowUseCase profiles,
            @Value("${app.testing-payments.enabled:false}") boolean testPaymentsEnabled) {
        this.documents = documents;
        this.closeFinancePeriod = closeFinancePeriod;
        this.financeOperations = financeOperations;
        this.absenceWorkflow = absenceWorkflow;
        this.salaryProposalWorkflow = salaryProposalWorkflow;
        this.contractLifecycle = contractLifecycle;
        this.incidentWorkflow = incidentWorkflow;
        this.management = management;
        this.contractRatings = contractRatings;
        this.profiles = profiles;
        this.testPaymentsEnabled = testPaymentsEnabled;
    }

    private Account me(Authentication auth) {
        return profiles.accountByEmail(auth.getName());
    }

    private boolean isParty(HireRequest r, Account a) {
        return r.getClient().getId().equals(a.getId()) || r.getHousekeeper().getAccount().getId().equals(a.getId());
    }

    private Map<String, Object> proposalRow(SalaryProposal p) {
        return Map.of("id", p.getId(), "requestId", p.getHireRequest().getId(), "authorId", p.getAuthor().getId(),
                "authorName", p.getAuthor().getName(), "monthlySalary", p.getMonthlySalary(), "status", p.getStatus(),
                "message", p.getMessage() == null ? "" : p.getMessage(), "createdAt", p.getCreatedAt());
    }

    public record TaskInput(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
            @NotBlank String skillCode, Boolean active) {
    }

    @GetMapping("/tasks")
    @PreAuthorize("isAuthenticated()")
    public List<AgencyTask> listTasks() {
        return management.activeTasks();
    }

    @PostMapping("/agent/tasks")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> createTask(@Valid @RequestBody TaskInput in, Authentication auth) {
        try {
            return ResponseEntity.status(201).body(
                    management.createTask(me(auth), in.name(), in.description(), in.skillCode()));
        } catch (ManagementConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", "Esta tarefa já existe."));
        } catch (ManagementValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/agent/tasks/{id}")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> updateTask(@PathVariable UUID id, @Valid @RequestBody TaskInput in, Authentication auth) {
        try {
            return ResponseEntity.ok(management.updateTask(me(auth), id, in.name(), in.description(),
                    in.active(), in.skillCode()));
        } catch (ManagementNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ManagementValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record FinancePolicyInput(@Min(1) @Max(31) int monthlyDivisor,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal overtimeHourlyRate,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal additionalServiceUnitRate,
            @Min(1) @Max(28) int paymentDueDay, @Min(1) @Max(90) int requestValidityDays,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal agencyFeePercent) {
    }

    @GetMapping("/agent/settings/finance")
    @PreAuthorize("hasRole('AGENT')")
    public AgencyFinancePolicy getFinancePolicy() {
        return management.financePolicy();
    }

    @PutMapping("/agent/settings/finance")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> updateFinancePolicy(@Valid @RequestBody FinancePolicyInput in, Authentication auth) {
        return ResponseEntity.ok(management.updateFinancePolicy(me(auth), in.monthlyDivisor(),
                in.overtimeHourlyRate(), in.additionalServiceUnitRate(), in.paymentDueDay(),
                in.requestValidityDays(), in.agencyFeePercent(), in.toString()));
    }

    public record TestDateInput(LocalDate date) {
    }

    @GetMapping("/agent/settings/test-date")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> getTestDate() {
        try {
            return ResponseEntity.ok(management.testDate());
        } catch (ManagementNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/agent/settings/test-date")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> updateTestDate(@RequestBody TestDateInput input, Authentication auth) {
        try {
            return ResponseEntity.ok(management.updateTestDate(me(auth), input.date()));
        } catch (ManagementNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/agent/settings/payment-testing")
    @PreAuthorize("hasRole('AGENT')")
    public Map<String, Boolean> getPaymentTestingSettings() {
        return Map.of("enabled", testPaymentsEnabled);
    }

    public record ProposalInput(
            @NotNull @DecimalMin("5000.00") @Digits(integer = 10, fraction = 2) BigDecimal monthlySalary,
            @Size(max = 500) String message) {
    }

    @GetMapping("/requests/{id}/proposals")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    @Transactional(readOnly = true)
    public ResponseEntity<?> proposalHistory(@PathVariable UUID id, Authentication auth) {
        var actor = me(auth);
        try {
            var history = salaryProposalWorkflow.history(id, actor.getId()).stream().map(this::proposalRow).toList();
            log.debug("Salary proposal history loaded: requestId={} actorId={} proposalCount={}", id, actor.getId(),
                    history.size());
            return ResponseEntity.ok(history);
        } catch (RequestNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (RequestForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/requests/{id}/proposals")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> counterOffer(@PathVariable UUID id, @Valid @RequestBody ProposalInput in,
            Authentication auth) {
        try {
            var proposal = salaryProposalWorkflow.counterOffer(id, me(auth).getId(),
                    in.monthlySalary(), in.message());
            return ResponseEntity.status(201).body(proposalRow(proposal));
        } catch (RequestNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (RequestForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        } catch (RequestConflictException exception) {
            return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
        } catch (RequestValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    public record ProposalDecision(@NotBlank String decision, @Size(max = 500) String message) {
    }

    @PatchMapping("/requests/{id}/proposals/{proposalId}")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> decideProposal(@PathVariable UUID id, @PathVariable UUID proposalId,
            @Valid @RequestBody ProposalDecision input, Authentication auth) {
        try {
            var result = salaryProposalWorkflow.decide(id, proposalId, me(auth).getId(),
                    input.decision(), input.message());
            return ResponseEntity.ok(Map.of(
                    "proposal", proposalRow(result.proposal()),
                    "requestStatus", result.requestStatus()));
        } catch (RequestNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (RequestForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        } catch (RequestConflictException exception) {
            return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
        } catch (RequestValidationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    public record AbsenceInput(@NotNull UUID contractId, @NotNull @PastOrPresent LocalDate date,
            @NotNull @DecimalMin("0.25") @Digits(integer = 3, fraction = 2) BigDecimal days,
            @Size(max = 1000) String notes) {
    }

    private Map<String, Object> absenceRow(AbsenceDetails a) {
        return Map.ofEntries(Map.entry("id", a.id()), Map.entry("contractId", a.contractId()),
                Map.entry("clientName", a.clientName()), Map.entry("workerName", a.workerName()),
                Map.entry("reportedBy", a.reportedBy()), Map.entry("date", a.date()), Map.entry("days", a.days()),
                Map.entry("notes", a.notes()), Map.entry("status", a.status()),
                Map.entry("justification", a.justification()), Map.entry("hasEvidence", a.hasEvidence()),
                Map.entry("evidenceContentType", a.evidenceContentType()), Map.entry("decisionNote", a.decisionNote()),
                Map.entry("createdAt", a.createdAt()));
    }

    @PostMapping("/absences")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> reportAbsence(@Valid @RequestBody AbsenceInput in, Authentication auth) {
        try {
            return ResponseEntity.status(201).body(absenceRow(absenceWorkflow.report(
                    me(auth).getId(), in.contractId(), in.date(), in.days(), in.notes())));
        } catch (AbsenceNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (AbsenceForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/absences")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT','SECRETARY')")
    public List<?> listAbsences(Authentication auth) {
        return absenceWorkflow.list(me(auth).getId()).stream().map(this::absenceRow).toList();
    }

    @PostMapping(value = "/absences/{id}/justification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> justifyAbsence(@PathVariable UUID id,
            @RequestPart(value = "text", required = false) @Size(max = 2000) String text,
            @RequestPart(value = "file", required = false) MultipartFile file, Authentication auth) throws IOException {
        String justification = text == null ? "" : text.trim();
        if (justification.isBlank() && (file == null || file.isEmpty()))
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Escreva a justificação ou anexe um comprovativo."));
        byte[] data = null;
        String type = null;
        if (file != null && !file.isEmpty()) {
            if (file.getSize() > 5_000_000)
                return ResponseEntity.badRequest().body(Map.of("error", "O comprovativo não pode exceder 5 MB."));
            type = file.getContentType();
            data = file.getBytes();
            if (!validEvidence(data, type))
                return ResponseEntity.badRequest().body(Map.of("error", "Use um PDF, JPEG ou PNG válido."));
        }
        try {
            return ResponseEntity.ok(absenceRow(absenceWorkflow.submitJustification(
                    id, me(auth).getId(), justification, data, type)));
        } catch (AbsenceNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (AbsenceForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        } catch (AbsenceInvalidStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PatchMapping("/absences/{id}/no-justification")
    @PreAuthorize("hasAnyRole('HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<?> declareNoJustification(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok(absenceRow(absenceWorkflow.declareNoJustification(id, me(auth).getId())));
        } catch (AbsenceNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (AbsenceForbiddenException exception) {
            return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
        } catch (AbsenceInvalidStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    private boolean validEvidence(byte[] b, String type) {
        if (b.length < 4 || type == null)
            return false;
        if (type.equals("application/pdf"))
            return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
        if (type.equals("image/png"))
            return b.length >= 8 && (b[0] & 255) == 137 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        if (type.equals("image/jpeg"))
            return b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
        return false;
    }

    @PatchMapping("/agent/absences/{id}/decision")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> decideAbsence(@PathVariable UUID id, @RequestParam String decision,
            @RequestParam(required = false) String note, Authentication auth) {
        try {
            return ResponseEntity.ok(absenceRow(absenceWorkflow.decide(id, me(auth).getId(), decision, note)));
        } catch (AbsenceNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (AbsenceInvalidStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/absences/{id}/evidence")
    @PreAuthorize("hasAnyRole('AGENT','HOUSEKEEPER','CANDIDATE')")
    public ResponseEntity<byte[]> getEvidence(@PathVariable UUID id, Authentication auth) {
        try {
            var absence = absenceWorkflow.evidence(id, me(auth).getId());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(absence.contentType()))
                    .cacheControl(CacheControl.noStore()).body(absence.content());
        } catch (AbsenceNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (AbsenceForbiddenException exception) {
            return ResponseEntity.status(403).build();
        }
    }

    public record AdditionalInput(@NotNull UUID contractId, @NotBlank @Size(max = 500) String description,
            @NotNull @FutureOrPresent LocalDate date,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal quantity,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice, String workType) {
    }

    private Map<String, Object> extraRow(AdditionalWork x) {
        return Map.ofEntries(Map.entry("id", x.getId()), Map.entry("contractId", x.getContract().getId()),
                Map.entry("description", x.getDescription()), Map.entry("date", x.getServiceDate()),
                Map.entry("quantity", x.getQuantity()), Map.entry("unitPrice", x.getUnitPrice()),
                Map.entry("total", x.getTotal()), Map.entry("status", x.getStatus()),
                Map.entry("workType", x.getWorkType()), Map.entry("createdBy", x.getCreatedBy().getName()),
                Map.entry("decisionNote", x.getDecisionNote() == null ? "" : x.getDecisionNote()));
    }

    @PostMapping("/additional-work")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    @Transactional
    public ResponseEntity<?> createAdditional(@Valid @RequestBody AdditionalInput in, Authentication auth) {
        try {
            var work = financeOperations.createAdditionalWork(me(auth).getId(), in.contractId(), in.description(),
                    in.date(), in.quantity(), in.unitPrice(), in.workType());
            return ResponseEntity.status(201).body(extraRow(work));
        } catch (FinanceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FinanceForbiddenException e) {
            return ResponseEntity.status(403).build();
        } catch (FinanceValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/additional-work")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    @Transactional(readOnly = true)
    public List<?> listAdditional(Authentication auth) {
        return financeOperations.listAdditionalWork(me(auth).getId()).stream().map(this::extraRow).toList();
    }

    @PatchMapping("/agent/additional-work/{id}")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> approveAdditional(@PathVariable UUID id, @RequestParam String decision,
            @RequestParam(required = false) String note, @RequestParam(required = false) BigDecimal unitPrice,
            Authentication auth) {
        try {
            return ResponseEntity.ok(
                    extraRow(financeOperations.decideAdditionalWork(id, me(auth).getId(), decision, note, unitPrice)));
        } catch (FinanceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FinanceValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/contracts/{id}/statement")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    public ResponseEntity<?> statement(@PathVariable UUID id, @RequestParam String period, Authentication auth) {
        try {
            return ResponseEntity.ok(financeOperations.statement(id, me(auth).getId(), period));
        } catch (FinanceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FinanceForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (FinanceValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/agent/finance/{contractId}/{period}/close")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> closeFinancePeriod(@PathVariable UUID contractId, @PathVariable String period,
            Authentication auth) {
        try {
            var closed = closeFinancePeriod.close(contractId, me(auth).getId(), period);
            return ResponseEntity.status(201).body(Map.of(
                    "id", closed.id(),
                    "contractId", closed.contractId(),
                    "period", closed.period(),
                    "closedAt", closed.closedAt()));
        } catch (ContractNotFoundException exception) {
            return ResponseEntity.notFound().build();
        } catch (FinancePeriodAlreadyClosedException exception) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "O período já está fechado. Use um ajuste auditável."));
        } catch (FinancePeriodCloseException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Não foi possível fechar o período financeiro."));
        }
    }

    public record AdjustmentInput(@NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    @PostMapping("/agent/finance/{contractId}/{period}/adjustments")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> addFinanceAdjustment(@PathVariable UUID contractId, @PathVariable String period,
            @Valid @RequestBody AdjustmentInput input, Authentication auth) {
        try {
            var adjustment = financeOperations.addAdjustment(contractId, period, me(auth).getId(), input.amount(),
                    input.reason(), input.toString());
            return ResponseEntity.status(201).body(Map.of("id", adjustment.getId(), "amount", adjustment.getAmount(),
                    "reason", adjustment.getReason(), "createdAt", adjustment.getCreatedAt()));
        } catch (FinanceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FinanceValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/contracts/{id}/statement.pdf")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    public ResponseEntity<byte[]> statementPdf(@PathVariable UUID id, @RequestParam String period,
            Authentication auth) {
        try {
            Map<String, Object> s = financeOperations.statement(id, me(auth).getId(), period);
            boolean closed = Boolean.TRUE.equals(s.get("closed"));
            String content = "DEMONSTRATIVO FINANCEIRO " + period + "\nContrato: " + id + "\nSalário base: "
                    + s.get("baseSalary") + " MZN\nHoras extraordinárias: " + s.get("overtime")
                    + " MZN\nServiços adicionais: " + s.get("additionalServices") + " MZN\nDesconto da empregada: "
                    + s.get("workerAbsenceDiscount") + " MZN\nDesconto do cliente: " + s.get("clientAbsenceDiscount")
                    + " MZN\nCompensação da agência: " + s.get("agencyCompensation") + " MZN\nLíquido da empregada: "
                    + s.get("workerNet") + " MZN\nTotal devido pelo cliente: " + s.get("clientDue")
                    + " MZN\nDivisor configurado: " + s.get("divisor") + " dias\nEstado: "
                    + (closed ? "FECHADO em " + s.get("closedAt") : "PROVISÓRIO") + "\n";
            Object workItems = s.get("additionalWorkItems");
            if (workItems instanceof Collection<?> list && !list.isEmpty())
                content += "\nSERVIÇOS E HORAS EXTRA APROVADOS\n" + String.join("\n", list.stream().map(item -> {
                    Map<?, ?> row = (Map<?, ?>) item;
                    return row.get("date") + " · " + row.get("workType") + " · " + row.get("description") + " · "
                            + row.get("quantity") + " × " + row.get("unitPrice") + " MZN = " + row.get("total")
                            + " MZN";
                }).toList());
            Object notes = s.get("calculationNotes");
            if (notes instanceof Collection<?> list)
                content += "\n" + String.join("\n", list.stream().map(String::valueOf).toList());
            Object adjustments = s.get("adjustments");
            if (adjustments instanceof Collection<?> list && !list.isEmpty())
                content += "\n\nAJUSTES POSTERIORES AUDITÁVEIS\n" + String.join("\n", list.stream().map(item -> {
                    Map<?, ?> row = (Map<?, ?>) item;
                    return row.get("amount") + " MZN · " + row.get("reason") + " · " + row.get("createdBy") + " · "
                            + row.get("createdAt");
                }).toList());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=demonstrativo-" + period + ".pdf")
                    .body(documents.renderPdf("Demonstrativo LarLink", content));
        } catch (FinanceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FinanceForbiddenException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public record VersionInput(@NotBlank @Size(min = 30, max = 30000) String content,
            @NotBlank @Size(min = 5, max = 500) String reason) {
    }

    @GetMapping("/contracts/{id}/versions")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    public ResponseEntity<?> contractVersions(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok(contractLifecycle.history(id, me(auth).getId()).stream()
                    .map(v -> Map.of("id", v.getId(), "version", v.getRevisionNumber(), "kind", v.getKind(),
                            "content", v.getContent(), "reason", v.getChangeReason() == null ? "" : v.getChangeReason(),
                            "createdAt", v.getCreatedAt(), "createdBy",
                            v.getCreatedBy() == null ? "Sistema" : v.getCreatedBy().getName())).toList());
        } catch (ContractLifecycleNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ContractForbiddenException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/agent/contracts/{id}/versions")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<?> createContractVersion(@PathVariable UUID id, @Valid @RequestBody VersionInput in,
            Authentication auth) {
        try {
            var version = contractLifecycle.revise(id, me(auth).getId(), in.content(), in.reason());
            return ResponseEntity.status(201).body(Map.of("id", version.getId(), "version", version.getRevisionNumber(),
                    "kind", version.getKind(), "content", version.getContent(), "createdAt", version.getCreatedAt()));
        } catch (ContractLifecycleNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ContractValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/contracts/{id}/pdf")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    public ResponseEntity<byte[]> contractPdf(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contrato-" + id + ".pdf")
                    .cacheControl(CacheControl.noStore()).body(contractLifecycle.pdf(id, me(auth).getId()));
        } catch (ContractLifecycleNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ContractForbiddenException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/contracts/{id}/payment-obligations")
    @PreAuthorize("hasAnyRole('CLIENT','AGENT')")
    @Transactional
    public ResponseEntity<?> createObligation(@PathVariable UUID id, @RequestParam String period, Authentication auth) {
        try {
            var result = financeOperations.createObligation(id, me(auth).getId(), period);
            return ResponseEntity.status(result.created() ? 201 : 200).body(paymentRow(result.obligation()));
        } catch (FinanceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FinanceForbiddenException e) {
            return ResponseEntity.status(403).build();
        } catch (FinanceValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> paymentRow(PaymentObligation p) {
        return Map.of("id", p.getId(), "contractId", p.getContract().getId(), "referenceMonth", p.getReferenceMonth(),
                "amount", p.getAmount(), "entity", p.getEntityCode() == null ? "" : p.getEntityCode(), "reference",
                p.getReferenceCode() == null ? "" : p.getReferenceCode(), "dueDate", p.getDueDate(), "status",
                p.getStatus(), "provider", p.getProvider() == null ? "" : p.getProvider(), "paidAt",
                p.getPaidAt() == null ? "" : p.getPaidAt());
    }

    @GetMapping("/payment-obligations")
    @PreAuthorize("hasAnyRole('CLIENT','AGENT')")
    public List<?> listObligations(Authentication auth) {
        return financeOperations.listObligations(me(auth).getId()).stream().map(this::paymentRow).toList();
    }

    @PostMapping("/agent/payment-obligations/{id}/issue-reference")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> issuePaymentReference(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok(paymentRow(financeOperations.issueReference(id, me(auth).getId())));
        } catch (FinanceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FinanceValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (PaymentProviderUnavailableException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                    "Prestador de pagamentos não configurado. Não foi criada uma referência fictícia."));
        } catch (PaymentReferenceException e) {
            return ResponseEntity.status(502)
                    .body(Map.of("error", "O prestador não devolveu entidade e referência válidas."));
        }
    }

    @PostMapping("/agent/payment-obligations/{id}/simulate-payment")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> simulatePayment(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok(paymentRow(financeOperations.simulatePayment(id, me(auth).getId())));
        } catch (FinanceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (FinanceConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/payments/webhook/{providerId}")
    @Transactional
    public ResponseEntity<?> paymentWebhook(@PathVariable String providerId,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        try {
            var result = financeOperations.handleCallback(providerId, rawBody, signature);
            return ResponseEntity.ok(Map.of("accepted", true, "duplicate", result.duplicate()));
        } catch (PaymentCallbackAuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Notificação do prestador não autenticada."));
        } catch (FinanceValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/notifications")
    @PreAuthorize("isAuthenticated()")
    public List<?> myNotifications(Authentication auth) {
        return management.notifications(me(auth).getId()).stream()
                .map(n -> Map.ofEntries(Map.entry("id", n.getId()), Map.entry("eventType", n.getEventType()),
                        Map.entry("message", n.getMessage()),
                        Map.entry("targetType", n.getTargetType() == null ? "" : n.getTargetType()),
                        Map.entry("targetId", n.getTargetId() == null ? "" : n.getTargetId().toString()),
                        Map.entry("createdAt", n.getCreatedAt()), Map.entry("read", n.isRead()),
                        Map.entry("readAt", n.getReadAt() == null ? "" : n.getReadAt().toString())))
                .toList();
    }

    @PatchMapping("/notifications/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<?> markNotification(@PathVariable UUID id, Authentication auth) {
        try {
            var n = management.markNotificationRead(id, me(auth).getId());
            return ResponseEntity.ok(Map.of("id", n.getId(), "read", n.isRead(), "readAt", n.getReadAt()));
        } catch (ManagementNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ManagementForbiddenException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/agent/audit")
    @PreAuthorize("hasRole('AGENT')")
    public List<?> auditLog() {
        return management.auditEvents().stream()
                .map(e -> Map.of("id", e.getId(), "actor", e.getActor() == null ? "system" : e.getActor().getName(),
                        "action", e.getAction(), "entityType", e.getEntityType(), "entityId",
                        e.getEntityId() == null ? "" : e.getEntityId(), "details",
                        e.getDetails() == null ? "" : e.getDetails(), "createdAt", e.getCreatedAt()))
                .toList();
    }

    public record RatingInput(@Min(1) @Max(5) int score, @Size(max = 1000) String comment) {
    }

    private Map<String, Object> ratingRow(ContractRating rating) {
        return Map.of("id", rating.getId(), "authorId", rating.getAuthor().getId(), "authorName",
                rating.getAuthor().getName(), "score", rating.getScore(), "comment",
                rating.getComment() == null ? "" : rating.getComment(), "createdAt", rating.getCreatedAt());
    }

    @GetMapping("/contracts/{id}/ratings")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE')")
    @Transactional(readOnly = true)
    public ResponseEntity<?> contractRatings(@PathVariable UUID id, Authentication auth) {
        try {
            var details = contractRatings.list(id, me(auth).getId());
            return ResponseEntity.ok(Map.of("eligible", details.eligible(), "ratings",
                    details.ratings().stream().map(this::ratingRow).toList(),
                    "average", Math.round(details.average() * 100.0) / 100.0,
                    "submittedByMe", details.submittedByMe()));
        } catch (ContractRatingNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ContractRatingForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/contracts/{id}/ratings")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE')")
    @Transactional
    public ResponseEntity<?> createContractRating(@PathVariable UUID id, @Valid @RequestBody RatingInput input,
            Authentication auth) {
        try {
            var saved = contractRatings.create(id, me(auth), input.score(), input.comment());
            return ResponseEntity.status(201).body(ratingRow(saved));
        } catch (ContractRatingNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ContractRatingForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (ContractRatingValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    public List<?> incidentList(Authentication auth) {
        var actor = me(auth);
        return incidentWorkflow.list(actor.getId()).stream()
                .map(i -> Map.ofEntries(Map.entry("id", i.getId()), Map.entry("contractId", i.getContract().getId()),
                        Map.entry("clientName", i.getContract().getHireRequest().getClient().getName()),
                        Map.entry("workerName", i.getContract().getHireRequest().getHousekeeper().getName()),
                        Map.entry("reporter", i.getReporter().getName()), Map.entry("type", i.getType()),
                        Map.entry("description", i.getDescription()), Map.entry("status", i.getStatus()),
                        Map.entry("decision", i.getAgencyDecision() == null ? "" : i.getAgencyDecision()),
                        Map.entry("createdAt", i.getCreatedAt()), Map.entry("hasEvidence", i.getEvidence() != null)))
                .toList();
    }

    public record IncidentStatusInput(@NotBlank String status, @Size(max = 2000) String decision) {
    }

    @PatchMapping("/agent/incidents/{id}/status")
    @PreAuthorize("hasRole('AGENT')")
    @Transactional
    public ResponseEntity<?> updateIncidentStatus(@PathVariable UUID id, @Valid @RequestBody IncidentStatusInput in,
            Authentication auth) {
        try {
            var result = incidentWorkflow.updateStatus(id, me(auth).getId(), in.status(), in.decision());
            return ResponseEntity.ok(Map.of("id", result.id(), "status", result.status()));
        } catch (IncidentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IncidentValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/incidents/{id}/messages")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    public ResponseEntity<?> incidentMessages(@PathVariable UUID id, Authentication auth) {
        try {
            return ResponseEntity.ok(incidentWorkflow
                    .messages(id, me(auth).getId()).stream().map(m -> Map.of("id", m.id(), "author", m.author(),
                            "message", m.message(), "hasAttachment", m.hasAttachment(), "createdAt", m.createdAt()))
                    .toList());
        } catch (IncidentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IncidentForbiddenException e) {
            return ResponseEntity.status(403).build();
        }
    }

    public record IncidentMessageInput(@NotBlank @Size(max = 2000) String message) {
    }

    @PostMapping("/incidents/{id}/messages/text")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    @Transactional
    public ResponseEntity<?> addIncidentText(@PathVariable UUID id, @Valid @RequestBody IncidentMessageInput input,
            Authentication auth) {
        return addIncidentMessageResponse(id, input.message(), null, null, auth);
    }

    @PostMapping(value = "/incidents/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    @Transactional
    public ResponseEntity<?> addIncidentMessage(@PathVariable UUID id,
            @RequestPart(value = "message", required = false) String message,
            @RequestPart(value = "file", required = false) MultipartFile file, Authentication auth) throws IOException {
        byte[] bytes = null;
        String type = null;
        if ((message == null || message.isBlank()) && (file == null || file.isEmpty()))
            return ResponseEntity.badRequest().body(Map.of("error", "Escreva uma mensagem ou anexe um documento."));
        if (message != null && message.trim().length() > 2000)
            return ResponseEntity.badRequest().body(Map.of("error", "A mensagem não pode exceder 2000 caracteres."));
        if (file != null && !file.isEmpty()) {
            if (file.getSize() > 5_000_000 || !validEvidence(file.getBytes(), file.getContentType()))
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Anexo inválido. Use PDF, JPEG ou PNG até 5 MB."));
            bytes = file.getBytes();
            type = file.getContentType();
        }
        return addIncidentMessageResponse(id, message, bytes, type, auth);
    }

    private ResponseEntity<?> addIncidentMessageResponse(UUID id, String message, byte[] bytes, String type,
            Authentication auth) {
        try {
            var saved = incidentWorkflow.addMessage(id, me(auth).getId(), message, bytes, type);
            return ResponseEntity.status(201).body(Map.of("id", saved.getId(), "createdAt", saved.getCreatedAt()));
        } catch (IncidentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IncidentForbiddenException e) {
            return ResponseEntity.status(403).build();
        } catch (IncidentValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/incident-messages/{id}/attachment")
    @PreAuthorize("hasAnyRole('CLIENT','HOUSEKEEPER','CANDIDATE','AGENT')")
    public ResponseEntity<byte[]> incidentAttachment(@PathVariable UUID id, Authentication auth) {
        try {
            var attachment = incidentWorkflow.attachment(id, me(auth).getId());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.contentType()))
                    .cacheControl(CacheControl.noStore()).body(attachment.content());
        } catch (IncidentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IncidentForbiddenException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/agent/reports")
    @PreAuthorize("hasRole('AGENT')")
    public Map<String, Object> reports(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) UUID clientId, @RequestParam(required = false) UUID workerId) {
        return management.reports(from, to, contractId, clientId, workerId);
    }
}
