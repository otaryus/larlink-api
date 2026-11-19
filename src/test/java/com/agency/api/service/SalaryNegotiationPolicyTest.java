package com.agency.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalaryNegotiationPolicyTest {

    private final SalaryNegotiationPolicy policy = new SalaryNegotiationPolicy();

    @Test
    void acceptsOffersAtOrAboveMinimumInFiftyMeticalIncrements() {
        assertDoesNotThrow(() -> policy.validateOffer(5_000));
        assertDoesNotThrow(() -> policy.validateOffer(10_050));
    }

    @Test
    void rejectsOffersOutsideFiftyMeticalIncrements() {
        var error = assertThrows(IllegalArgumentException.class, () -> policy.validateOffer(5_251));

        assertEquals("O salário proposto deve ser um múltiplo de 50 MT.", error.getMessage());
    }

    @Test
    void rejectsOffersBelowTheLegalMinimum() {
        var error = assertThrows(IllegalArgumentException.class, () -> policy.validateOffer(4_950));

        assertEquals("O salário proposto deve ser igual ou superior a 5.000 MT.", error.getMessage());
    }
}
