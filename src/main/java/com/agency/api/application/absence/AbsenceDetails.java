package com.agency.api.application.absence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AbsenceDetails(
        UUID id,
        UUID contractId,
        String clientName,
        String workerName,
        String reportedBy,
        LocalDate date,
        BigDecimal days,
        String notes,
        String status,
        String justification,
        boolean hasEvidence,
        String evidenceContentType,
        String decisionNote,
        Instant createdAt) {
}
