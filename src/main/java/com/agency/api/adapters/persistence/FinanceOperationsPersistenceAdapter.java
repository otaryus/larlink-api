package com.agency.api.adapters.persistence;

import com.agency.api.application.finance.FinanceOperationsPort;
import com.agency.api.application.finance.FinancePeriodCloseException;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import com.agency.api.service.FinanceCalculationService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;

@Component
public class FinanceOperationsPersistenceAdapter implements FinanceOperationsPort {
    private final AccountRepository accounts;
    private final ContractRepository contracts;
    private final AdditionalWorkRepository additionalWork;
    private final PaymentObligationRepository payments;
    private final AgencyFinancePolicyRepository policies;
    private final FinancialCloseRepository closes;
    private final FinanceAdjustmentRepository adjustments;
    private final FinanceCalculationService calculations;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public FinanceOperationsPersistenceAdapter(AccountRepository accounts, ContractRepository contracts,
            AdditionalWorkRepository additionalWork, PaymentObligationRepository payments,
            AgencyFinancePolicyRepository policies, FinancialCloseRepository closes,
            FinanceAdjustmentRepository adjustments, FinanceCalculationService calculations,
            AuditService audit, ObjectMapper mapper) {
        this.accounts = accounts;
        this.contracts = contracts;
        this.additionalWork = additionalWork;
        this.payments = payments;
        this.policies = policies;
        this.closes = closes;
        this.adjustments = adjustments;
        this.calculations = calculations;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Override public Optional<Account> findAccount(UUID id) { return accounts.findById(id); }
    @Override public Optional<EmploymentContract> findContract(UUID id) { return contracts.findById(id); }
    @Override public Optional<EmploymentContract> lockContract(UUID id) { return contracts.findLockedById(id); }
    @Override public Optional<AdditionalWork> additionalWork(UUID id) { return additionalWork.findById(id); }
    @Override public List<AdditionalWork> allAdditionalWork() { return additionalWork.findAllByOrderByCreatedAtDesc(); }
    @Override public AdditionalWork saveAdditionalWork(AdditionalWork work) { return additionalWork.save(work); }
    @Override public Optional<PaymentObligation> payment(UUID id) { return payments.findById(id); }
    @Override public Optional<PaymentObligation> payment(UUID contractId, String period) {
        return payments.findByContractIdAndReferenceMonth(contractId, period);
    }
    @Override public Optional<PaymentObligation> paymentByTransaction(String transactionId) {
        return payments.findByProviderTransactionId(transactionId);
    }
    @Override public List<PaymentObligation> paymentsForClient(UUID clientId) {
        return payments.findByContractHireRequestClientIdOrderByDueDateDesc(clientId);
    }
    @Override public List<PaymentObligation> allPayments() { return payments.findAllByOrderByDueDateDesc(); }
    @Override public PaymentObligation savePayment(PaymentObligation obligation) { return payments.save(obligation); }
    @Override public int paymentDueDay() { return policies.findById("agency").map(AgencyFinancePolicy::getPaymentDueDay).orElse(5); }

    @Override
    public BigDecimal configuredUnitPrice(String workType) {
        return policies.findById("agency")
                .map(policy -> "OVERTIME".equals(workType)
                        ? policy.getOvertimeHourlyRate() : policy.getAdditionalServiceUnitRate())
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> statement(UUID contractId, String period) {
        try {
            EmploymentContract contract = contracts.findById(contractId).orElseThrow();
            Optional<FinancialClose> close = closes.findByContractIdAndReferenceMonth(contractId, period);
            if (close.isEmpty()) return mapper.convertValue(calculations.calculate(contract, period), Map.class);
            Map<String, Object> view = mapper.readValue(close.get().getSnapshotJson(), Map.class);
            if (!view.containsKey("additionalWorkItems")) {
                view.put("additionalWorkItems", calculations.calculate(contract, period).additionalWorkItems());
            }
            view.put("closed", true);
            view.put("closedAt", close.get().getClosedAt());
            view.put("adjustments", adjustments.findByFinancialCloseIdOrderByCreatedAtAsc(close.get().getId())
                    .stream().map(adjustment -> Map.of(
                            "id", adjustment.getId(),
                            "amount", adjustment.getAmount(),
                            "reason", adjustment.getReason(),
                            "createdBy", adjustment.getCreatedBy().getName(),
                            "createdAt", adjustment.getCreatedAt()))
                    .toList());
            return view;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid finance statement", exception);
        }
    }

    @Override
    public FinanceAdjustment createAdjustment(UUID contractId, String period, UUID actorId, BigDecimal amount,
            String reason, String auditDetails) {
        FinancialClose close = closes.findByContractIdAndReferenceMonth(contractId, period)
                .orElseThrow(() -> new FinancePeriodCloseException(new IllegalStateException("Period is not closed")));
        Account actor = accounts.findById(actorId).orElseThrow();
        FinanceAdjustment adjustment = adjustments.save(new FinanceAdjustment(close, actor, amount, reason));
        audit.record(actor, "FINANCE_ADJUSTMENT_CREATED", "FinanceAdjustment", adjustment.getId(),
                auditDetails);
        return adjustment;
    }

    @Override public void record(Account actor, String action, String entityType, UUID entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }
    @Override public void notify(UUID accountId, String type, String message, String entityType, UUID entityId) {
        audit.notify(accountId, type, message, entityType, entityId);
    }
    @Override public void notifyRole(Role role, String type, String message, String entityType, UUID entityId) {
        audit.notifyRole(role, type, message, entityType, entityId);
    }
}
