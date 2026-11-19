package com.agency.api.service;

import com.agency.api.application.matching.MatchDistancePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchDistancePolicyTest {
    private final MatchDistancePolicy policy = new MatchDistancePolicy();

    @Test
    void acceptsCandidatesUpToTwentyFiveKilometersAndRewardsCloserCandidates() {
        var samePlace = policy.evaluate(-25.9653, 32.5892, -25.9653, 32.5892);
        var nearby = policy.evaluate(-25.9653, 32.5892, -25.9000, 32.6000);
        var boundary = policy.evaluate(-25.9653, 32.5892, -25.7405, 32.5892);
        var tooFar = policy.evaluate(-25.9653, 32.5892, -25.7000, 32.5892);

        assertTrue(samePlace.eligible());
        assertEquals(100, samePlace.proximityScore());
        assertTrue(nearby.eligible());
        assertTrue(nearby.proximityScore() < samePlace.proximityScore());
        assertTrue(boundary.eligible());
        assertTrue(boundary.distanceKm() <= 25.0);
        assertFalse(tooFar.eligible());
        assertEquals(0, tooFar.proximityScore());
    }

    @Test
    void usesInclusiveMaximumDistance() {
        assertTrue(policy.isEligible(25.0));
        assertFalse(policy.isEligible(25.01));
        assertEquals(50, policy.proximityScore(12.5));
    }
}
