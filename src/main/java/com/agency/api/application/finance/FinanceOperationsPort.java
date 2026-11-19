package com.agency.api.application.finance;

import com.agency.api.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface FinanceOperationsPort {
    Optional<Account> findAccount(UUID id);
    Optional<EmploymentContract> findContract(UUID id);
    Optional<EmploymentContract> lockContract(UUID id);
    Optional<AdditionalWork> additionalWork(UUID id);
    List<AdditionalWork> allAdditionalWork();
    AdditionalWork saveAdditionalWork(AdditionalWork work);
    Optional<PaymentObligation> payment(UUID id);
    Optional<PaymentObligation> payment(UUID contractId, String period);
    Optional<PaymentObligation> paymentByTransaction(String transactionId);
    List<PaymentObligation> paymentsForClient(UUID clientId);
    List<PaymentObligation> allPayments();
    PaymentObligation savePayment(PaymentObligation obligation);
    int paymentDueDay();
    BigDecimal configuredUnitPrice(String workType);
    Map<String, Object> statement(UUID contractId, String period);
    FinanceAdjustment createAdjustment(UUID contractId, String period, UUID actorId, BigDecimal amount, String reason,
            String auditDetails);
    void record(Account actor, String action, String entityType, UUID entityId, String details);
    void notify(UUID accountId, String type, String message, String entityType, UUID entityId);
    void notifyRole(Role role, String type, String message, String entityType, UUID entityId);
}
