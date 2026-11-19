package com.agency.api.application.matching;

import org.springframework.stereotype.Service;

@Service
public class MatchScorePolicy {
    public static final int DETERMINISTIC_WEIGHT_PERCENT = 50;
    public static final int AI_WEIGHT_PERCENT = 50;

    public Score combine(int deterministicScore, Integer aiPosition, int candidateCount) {
        int normalizedDeterministic = clamp(deterministicScore);
        if (aiPosition == null || candidateCount < 2 || aiPosition < 0 || aiPosition >= candidateCount) {
            return new Score(normalizedDeterministic, normalizedDeterministic, null, 100, 0);
        }
        int aiScore = (int) Math.round((candidateCount - 1 - aiPosition) * 100.0 / (candidateCount - 1));
        int total = (int) Math.round((normalizedDeterministic * DETERMINISTIC_WEIGHT_PERCENT
                + aiScore * AI_WEIGHT_PERCENT) / 100.0);
        return new Score(total, normalizedDeterministic, aiScore, DETERMINISTIC_WEIGHT_PERCENT, AI_WEIGHT_PERCENT);
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    public record Score(int total, int deterministic, Integer ai, int deterministicWeight, int aiWeight) {}
}
