package com.agency.api.application.finance;

import com.agency.api.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class FinanceOperationsUseCase {
    private static final Set<String> WORK_TYPES = Set.of("OVERTIME", "ADDITIONAL_SERVICE");
    private final FinanceOperationsPort finance;
    private final PaymentGatewayPort paymentGateway;
    private final boolean testPaymentsEnabled;

    public FinanceOperationsUseCase(FinanceOperationsPort finance, PaymentGatewayPort paymentGateway,
            @Value("${app.testing-payments.enabled:false}") boolean testPaymentsEnabled) {
        this.finance = finance;
        this.paymentGateway = paymentGateway;
        this.testPaymentsEnabled = testPaymentsEnabled;
    }

    @Transactional
    public AdditionalWork createAdditionalWork(UUID actorId, UUID contractId, String description, LocalDate date,
            BigDecimal quantity, BigDecimal unitPrice, String type) {
        EmploymentContract contract = contract(contractId);
        Account actor = account(actorId);
        if (actor.getRole() != Role.AGENT && !isParty(contract.getHireRequest(), actor)) {
            throw new FinanceForbiddenException("");
        }
        String workType = type == null || type.isBlank() ? "ADDITIONAL_SERVICE" : type.toUpperCase(Locale.ROOT);
        if (!WORK_TYPES.contains(workType)) throw new FinanceValidationException("Tipo de serviço inválido.");
        AdditionalWork work = finance.saveAdditionalWork(new AdditionalWork(contract, actor, description.trim(),
                date, quantity, unitPrice, workType));
        finance.record(actor, "ADDITIONAL_WORK_CREATED", "AdditionalWork", work.getId(), work.getDescription());
        HireRequest request = contract.getHireRequest();
        finance.notify(request.getClient().getId(), "ADDITIONAL_WORK",
                "Foi registado um serviço adicional para aprovação.", "AdditionalWork", work.getId());
        finance.notify(request.getHousekeeper().getAccount().getId(), "ADDITIONAL_WORK",
                "Foi registado um serviço adicional para aprovação.", "AdditionalWork", work.getId());
        finance.notifyRole(Role.AGENT, "ADDITIONAL_WORK",
                "Um serviço adicional aguarda validação.", "AdditionalWork", work.getId());
        return work;
    }

    @Transactional(readOnly = true)
    public List<AdditionalWork> listAdditionalWork(UUID actorId) {
        Account actor = account(actorId);
        return finance.allAdditionalWork().stream()
                .filter(work -> actor.getRole() == Role.AGENT
                        || isParty(work.getContract().getHireRequest(), actor))
                .toList();
    }

    @Transactional
    public AdditionalWork decideAdditionalWork(UUID workId, UUID actorId, String decision, String note,
            BigDecimal unitPrice) {
        Account actor = account(actorId);
        requireAgent(actor);
        AdditionalWork work = finance.additionalWork(workId).orElseThrow(FinanceNotFoundException::new);
        String normalized = decision.toUpperCase(Locale.ROOT);
        BigDecimal approvedPrice = unitPrice;
        if ("APPROVED".equals(normalized) && approvedPrice == null) {
            BigDecimal configured = finance.configuredUnitPrice(work.getWorkType());
            if (configured != null && configured.signum() > 0) approvedPrice = configured;
        }
        try {
            work.decide(normalized, actor, note, approvedPrice);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new FinanceValidationException(exception.getMessage());
        }
        finance.record(actor, "ADDITIONAL_WORK_" + decision, "AdditionalWork", workId, note);
        return work;
    }

    @Transactional
    public PaymentObligationResult createObligation(UUID contractId, UUID actorId, String period) {
        Account actor = account(actorId);
        EmploymentContract contract = finance.lockContract(contractId).orElseThrow(FinanceNotFoundException::new);
        if (actor.getRole() == Role.CLIENT) {
            if (!contract.getHireRequest().getClient().getId().equals(actor.getId())) {
                throw new FinanceForbiddenException("");
            }
        } else {
            requireAgent(actor);
        }
        if (!"ACTIVE".equals(contract.getStatus())) {
            throw new FinanceValidationException("Só contratos activos podem gerar obrigações.");
        }
        YearMonth month;
        try {
            month = YearMonth.parse(period);
        } catch (RuntimeException exception) {
            throw new FinanceValidationException("Use o período AAAA-MM.");
        }
        Optional<PaymentObligation> existing = finance.payment(contractId, period);
        if (existing.isPresent()) return new PaymentObligationResult(existing.get(), false);
        Map<String, Object> statement = finance.statement(contractId, period);
        BigDecimal amount = (BigDecimal) statement.get("clientDue");
        int dueDay = finance.paymentDueDay();
        YearMonth dueMonth = month.plusMonths(1);
        PaymentObligation obligation = finance.savePayment(new PaymentObligation(contract, period, amount,
                dueMonth.atDay(Math.min(dueDay, dueMonth.lengthOfMonth()))));
        finance.record(actor, "PAYMENT_OBLIGATION_CREATED", "PaymentObligation", obligation.getId(),
                period + "=" + obligation.getAmount());
        return new PaymentObligationResult(obligation, true);
    }

    @Transactional(readOnly = true)
    public List<PaymentObligation> listObligations(UUID actorId) {
        Account actor = account(actorId);
        if (actor.getRole() == Role.AGENT) return finance.allPayments();
        if (actor.getRole() == Role.CLIENT) return finance.paymentsForClient(actorId);
        throw new FinanceForbiddenException("");
    }

    @Transactional
    public PaymentObligation issueReference(UUID obligationId, UUID actorId) {
        Account actor = account(actorId);
        requireAgent(actor);
        PaymentObligation obligation = payment(obligationId);
        if (Set.of("PAGO", "SIMULADO").contains(obligation.getStatus())) {
            throw new FinanceValidationException(
                    "A obrigação já foi concluída e não pode receber uma nova referência.");
        }
        if (!paymentGateway.isConfigured()) throw new PaymentProviderUnavailableException();
        HireRequest request = obligation.getContract().getHireRequest();
        PaymentGatewayPort.IssuedReference reference = paymentGateway.issue(new PaymentGatewayPort.Invoice(
                obligation.getId(), obligation.getAmount(), request.getClient().getName(),
                "LarLink " + obligation.getReferenceMonth()));
        if (reference == null || reference.entity() == null || reference.reference() == null) {
            throw new PaymentReferenceException();
        }
        obligation.assignProviderReference(reference.provider(), reference.entity(), reference.reference());
        finance.record(actor, "PAYMENT_REFERENCE_ISSUED", "PaymentObligation", obligationId, reference.provider());
        return obligation;
    }

    @Transactional
    public PaymentObligation simulatePayment(UUID obligationId, UUID actorId) {
        if (!testPaymentsEnabled) throw new FinanceNotFoundException();
        Account actor = account(actorId);
        requireAgent(actor);
        PaymentObligation obligation = payment(obligationId);
        try {
            obligation.simulatePaid("TEST-" + UUID.randomUUID());
        } catch (IllegalStateException exception) {
            throw new FinanceConflictException(exception.getMessage());
        }
        finance.record(actor, "PAYMENT_SIMULATED", "PaymentObligation", obligationId,
                "Simulação local de teste; não houve transferência real.");
        return obligation;
    }

    @Transactional
    public PaymentCallbackResult handleCallback(String providerId, String rawBody, String signature) {
        if (!paymentGateway.isConfigured() || signature == null
                || !paymentGateway.verifyCallback(rawBody, signature)) {
            throw new PaymentCallbackAuthenticationException();
        }
        PaymentGatewayPort.Callback callback;
        try {
            callback = paymentGateway.parseCallback(rawBody);
        } catch (Exception exception) {
            throw new FinanceValidationException("Notificação de pagamento inválida.");
        }
        if (callback == null || callback.obligationId() == null || callback.amount() == null) {
            throw new FinanceValidationException("Notificação de pagamento inválida.");
        }
        String transaction = callback.transactionId();
        if (transaction == null || transaction.isBlank()) {
            throw new FinanceValidationException("Transação sem identificador.");
        }
        if (finance.paymentByTransaction(transaction).isPresent()) return new PaymentCallbackResult(true);
        PaymentObligation obligation = payment(callback.obligationId());
        if (!providerId.equals(obligation.getProvider())) {
            throw new FinanceValidationException("Obrigação ou prestador não corresponde.");
        }
        try {
            obligation.confirmPaid(transaction, callback.amount());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new FinanceValidationException(exception.getMessage());
        }
        finance.record(null, "PAYMENT_CONFIRMED", "PaymentObligation", obligation.getId(),
                "Transação " + transaction);
        HireRequest request = obligation.getContract().getHireRequest();
        finance.notify(request.getClient().getId(), "PAYMENT_CONFIRMED",
                "Pagamento confirmado para " + obligation.getReferenceMonth() + ".",
                "PaymentObligation", obligation.getId());
        finance.notify(request.getHousekeeper().getAccount().getId(), "PAYMENT_CONFIRMED",
                "Pagamento mensal confirmado.", "PaymentObligation", obligation.getId());
        finance.notifyRole(Role.AGENT, "PAYMENT_CONFIRMED",
                "Pagamento confirmado pelo prestador.", "PaymentObligation", obligation.getId());
        return new PaymentCallbackResult(false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> statement(UUID contractId, UUID actorId, String period) {
        EmploymentContract contract = contract(contractId);
        Account actor = account(actorId);
        if (actor.getRole() != Role.AGENT && !isParty(contract.getHireRequest(), actor)) {
            throw new FinanceForbiddenException("Sem acesso ao demonstrativo.");
        }
        try {
            YearMonth.parse(period);
            return finance.statement(contractId, period);
        } catch (RuntimeException exception) {
            throw new FinanceValidationException("Período inválido. Use AAAA-MM.");
        }
    }

    @Transactional
    public FinanceAdjustment addAdjustment(UUID contractId, String period, UUID actorId, BigDecimal amount,
            String reason, String auditDetails) {
        Account actor = account(actorId);
        requireAgent(actor);
        try {
            YearMonth.parse(period);
        } catch (RuntimeException exception) {
            throw new FinanceValidationException("Período inválido. Use AAAA-MM.");
        }
        try {
            return finance.createAdjustment(contractId, period, actorId, amount, reason.trim(), auditDetails);
        } catch (FinancePeriodCloseException exception) {
            throw new FinanceValidationException("Feche o período antes de lançar ajuste.");
        }
    }

    private Account account(UUID id) {
        return finance.findAccount(id).orElseThrow(FinanceNotFoundException::new);
    }

    private EmploymentContract contract(UUID id) {
        return finance.findContract(id).orElseThrow(FinanceNotFoundException::new);
    }

    private PaymentObligation payment(UUID id) {
        return finance.payment(id).orElseThrow(FinanceNotFoundException::new);
    }

    private boolean isParty(HireRequest request, Account actor) {
        return request.getClient().getId().equals(actor.getId())
                || request.getHousekeeper().getAccount().getId().equals(actor.getId());
    }

    private void requireAgent(Account actor) {
        if (actor.getRole() != Role.AGENT) throw new FinanceForbiddenException("");
    }
}
