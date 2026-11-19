package com.agency.api.service;

import com.agency.api.application.matching.MatchScorePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchScorePolicyTest {
    private final MatchScorePolicy policy = new MatchScorePolicy();

    @Test
    void combinesDeterministicAndAiScoresWithEqualWeights() {
        var score = policy.combine(80, 1, 3);

        assertEquals(65, score.total());
        assertEquals(80, score.deterministic());
        assertEquals(50, score.ai());
        assertEquals(50, score.deterministicWeight());
        assertEquals(50, score.aiWeight());
    }

    @Test
    void usesOnlyDeterministicScoreWhenAiRankingIsUnavailable() {
        var score = policy.combine(73, null, 4);

        assertEquals(73, score.total());
        assertEquals(73, score.deterministic());
        assertNull(score.ai());
        assertEquals(100, score.deterministicWeight());
        assertEquals(0, score.aiWeight());
    }

    @Test
    void ignoresAiRankingWhenThereIsOnlyOneCandidate() {
        var score = policy.combine(61, 0, 1);

        assertEquals(61, score.total());
        assertNull(score.ai());
        assertEquals(100, score.deterministicWeight());
    }
}
