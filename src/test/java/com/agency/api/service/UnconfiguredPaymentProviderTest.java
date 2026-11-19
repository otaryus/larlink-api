package com.agency.api.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnconfiguredPaymentProviderTest {

    private final UnconfiguredPaymentProvider provider = new UnconfiguredPaymentProvider();

    @Test
    void neverIssuesOrValidatesPaymentsWhenNotConfigured() {
        assertFalse(provider.isConfigured());
        assertFalse(provider.verifyCallback("{}", "signature"));
        var invoice = new PaymentProvider.Invoice(UUID.randomUUID(), BigDecimal.TEN, "Cliente", "Mensalidade");
        var error = assertThrows(IllegalStateException.class, () -> provider.issue(invoice));
        assertEquals("Nenhum prestador de pagamentos está configurado.", error.getMessage());
    }
}
