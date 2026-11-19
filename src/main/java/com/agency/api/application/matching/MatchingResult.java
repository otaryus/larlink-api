package com.agency.api.application.matching;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MatchingResult(
        UUID id,
        UUID accountId,
        String name,
        String city,
        String district,
        String neighborhood,
        Set<String> skills,
        Set<String> availability,
        int expectedSalary,
        int yearsOfExperience,
        String status,
        int score,
        int deterministicScore,
        Integer aiScore,
        int deterministicWeight,
        int aiWeight,
        Double distanceKm,
        Integer proximityScore,
        double maximumDistanceKm,
        List<String> missingSkills,
        List<String> missingAvailability,
        boolean canRequest,
        String matchReason,
        long completedContracts,
        String agencyRecommendation) {
}
