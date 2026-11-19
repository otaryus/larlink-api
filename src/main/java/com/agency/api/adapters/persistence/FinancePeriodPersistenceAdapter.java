package com.agency.api.adapters.persistence;

import com.agency.api.application.finance.ClosedFinancePeriod;
import com.agency.api.application.finance.FinancePeriodPort;
import com.agency.api.domain.FinancialClose;
import com.agency.api.repository.AccountRepository;
import com.agency.api.repository.ContractRepository;
import com.agency.api.repository.FinancialCloseRepository;
import com.agency.api.service.AuditService;
import com.agency.api.service.FinanceCalculationService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class FinancePeriodPersistenceAdapter implements FinancePeriodPort {
    private final ContractRepository contracts;
    private final AccountRepository accounts;
    private final FinancialCloseRepository closes;
    private final FinanceCalculationService finance;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public FinancePeriodPersistenceAdapter(
            ContractRepository contracts,
            AccountRepository accounts,
            FinancialCloseRepository closes,
            FinanceCalculationService finance,
            AuditService audit,
            ObjectMapper mapper) {
        this.contracts = contracts;
        this.accounts = accounts;
        this.closes = closes;
        this.finance = finance;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Override
    public boolean contractExists(UUID contractId) {
        return contracts.existsById(contractId);
    }

    @Override
    public boolean isClosed(UUID contractId, String period) {
        return closes.findByContractIdAndReferenceMonth(contractId, period).isPresent();
    }

    @Override
    public String statementSnapshot(UUID contractId, String period) throws Exception {
        var contract = contracts.findById(contractId).orElseThrow();
        return mapper.writeValueAsString(finance.calculate(contract, period));
    }

    @Override
    public ClosedFinancePeriod save(UUID contractId, UUID actorId, String period, String snapshot) {
        var contract = contracts.findById(contractId).orElseThrow();
        var actor = accounts.findById(actorId).orElseThrow();
        FinancialClose close = closes.save(new FinancialClose(contract, actor, period, snapshot));
        audit.record(actor, "FINANCE_PERIOD_CLOSED", "FinancialClose", close.getId(), contractId + " " + period);
        return new ClosedFinancePeriod(close.getId(), contractId, period, close.getClosedAt());
    }
}
