package com.agency.api.application.finance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CloseFinancePeriodUseCase {
    private final FinancePeriodPort financePeriods;

    public CloseFinancePeriodUseCase(FinancePeriodPort financePeriods) {
        this.financePeriods = financePeriods;
    }

    @Transactional
    public ClosedFinancePeriod close(UUID contractId, UUID actorId, String period) {
        if (!financePeriods.contractExists(contractId)) throw new ContractNotFoundException();
        if (financePeriods.isClosed(contractId, period)) throw new FinancePeriodAlreadyClosedException();
        try {
            String snapshot = financePeriods.statementSnapshot(contractId, period);
            return financePeriods.save(contractId, actorId, period, snapshot);
        } catch (Exception exception) {
            throw new FinancePeriodCloseException(exception);
        }
    }
}
