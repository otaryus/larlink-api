package com.agency.api.application.finance;

import java.time.Instant;
import java.util.UUID;

public record ClosedFinancePeriod(UUID id, UUID contractId, String period, Instant closedAt) {
}
