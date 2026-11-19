package com.agency.api.application.finance;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CloseFinancePeriodUseCaseTest {
    private final UUID contractId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final InMemoryFinancePeriodPort port = new InMemoryFinancePeriodPort();
    private final CloseFinancePeriodUseCase useCase = new CloseFinancePeriodUseCase(port);

    @Test
    void closesContractPeriodWithCalculatedSnapshotAndActor() {
        port.contractExists = true;
        port.snapshot = "{\"total\":123}";

        ClosedFinancePeriod result = useCase.close(contractId, actorId, "2026-10");

        assertEquals(contractId, result.contractId());
        assertEquals("2026-10", result.period());
        assertEquals(actorId, port.savedActor);
        assertEquals("{\"total\":123}", port.savedSnapshot);
    }

    @Test
    void rejectsUnknownContractWithoutCalculatingOrSaving() {
        assertThrows(ContractNotFoundException.class, () -> useCase.close(contractId, actorId, "2026-10"));
        assertFalse(port.snapshotRequested);
        assertFalse(port.saved);
    }

    @Test
    void preventsClosingTheSamePeriodTwice() {
        port.contractExists = true;
        port.alreadyClosed = true;

        assertThrows(FinancePeriodAlreadyClosedException.class,
                () -> useCase.close(contractId, actorId, "2026-10"));
        assertFalse(port.snapshotRequested);
        assertFalse(port.saved);
    }

    @Test
    void reportsSnapshotFailureWithoutPersistingPartialClose() {
        port.contractExists = true;
        port.snapshotFailure = new IllegalArgumentException("Invalid period");

        assertThrows(FinancePeriodCloseException.class,
                () -> useCase.close(contractId, actorId, "bad-period"));
        assertFalse(port.saved);
    }

    private static final class InMemoryFinancePeriodPort implements FinancePeriodPort {
        private boolean contractExists;
        private boolean alreadyClosed;
        private boolean snapshotRequested;
        private boolean saved;
        private String snapshot = "{}";
        private RuntimeException snapshotFailure;
        private UUID savedActor;
        private String savedSnapshot;

        @Override
        public boolean contractExists(UUID contractId) {
            return contractExists;
        }

        @Override
        public boolean isClosed(UUID contractId, String period) {
            return alreadyClosed;
        }

        @Override
        public String statementSnapshot(UUID contractId, String period) {
            snapshotRequested = true;
            if (snapshotFailure != null) throw snapshotFailure;
            return snapshot;
        }

        @Override
        public ClosedFinancePeriod save(UUID contractId, UUID actorId, String period, String snapshot) {
            saved = true;
            savedActor = actorId;
            savedSnapshot = snapshot;
            return new ClosedFinancePeriod(UUID.randomUUID(), contractId, period, Instant.EPOCH);
        }
    }
}
