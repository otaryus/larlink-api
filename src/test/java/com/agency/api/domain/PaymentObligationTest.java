package com.agency.api.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PaymentObligationTest {

    @Test
    void paymentConfirmationIsIdempotent() {
        var obligation = new PaymentObligation(null, "2026-11", new BigDecimal("12500.00"), LocalDate.of(2026, 11, 5));

        assertTrue(obligation.confirmPaid("transaction-1", new BigDecimal("12500.00")));
        assertFalse(obligation.confirmPaid("transaction-2", new BigDecimal("12500.00")));
        assertEquals("PAGO", obligation.getStatus());
        assertNotNull(obligation.getPaidAt());
    }

    @Test
    void rejectsAConfirmationWithDifferentAmount() {
        var obligation = new PaymentObligation(null, "2026-11", new BigDecimal("12500.00"), LocalDate.of(2026, 11, 5));

        var error = assertThrows(IllegalArgumentException.class,
                () -> obligation.confirmPaid("transaction-1", new BigDecimal("12499.00")));

        assertEquals("O valor confirmado não corresponde à obrigação.", error.getMessage());
        assertEquals("PENDENTE", obligation.getStatus());
    }

    @Test
    void simulatedPaymentIsDistinctFromARealProviderConfirmation() {
        var obligation = new PaymentObligation(null, "2026-11", new BigDecimal("12500.00"), LocalDate.of(2026, 11, 5));

        obligation.simulatePaid("TEST-transaction-1");

        assertEquals("SIMULADO", obligation.getStatus());
        assertEquals("SIMULAÇÃO LOCAL", obligation.getProvider());
        assertNotNull(obligation.getPaidAt());
        assertThrows(IllegalStateException.class, () -> obligation.simulatePaid("TEST-transaction-2"));
        assertThrows(IllegalStateException.class,
                () -> obligation.confirmPaid("provider-transaction-1", new BigDecimal("12500.00")));
        assertEquals("SIMULADO", obligation.getStatus());
    }

    @Test
    void onlyPendingPastDueObligationsExpire() {
        var overdue = new PaymentObligation(null, "2026-10", new BigDecimal("10000.00"), LocalDate.of(2026, 10, 5));
        overdue.expireIfDue(LocalDate.of(2026, 11, 19));
        assertEquals("EXPIRADO", overdue.getStatus());

        var paid = new PaymentObligation(null, "2026-10", new BigDecimal("10000.00"), LocalDate.of(2026, 10, 5));
        paid.confirmPaid("transaction-2", new BigDecimal("10000.00"));
        paid.expireIfDue(LocalDate.of(2026, 11, 19));
        assertEquals("PAGO", paid.getStatus());
    }
}
