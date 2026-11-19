package com.agency.api.application.finance;

import java.util.UUID;

public interface FinancePeriodPort {
    boolean contractExists(UUID contractId);

    boolean isClosed(UUID contractId, String period);

    String statementSnapshot(UUID contractId, String period) throws Exception;

    ClosedFinancePeriod save(UUID contractId, UUID actorId, String period, String snapshot);
}
