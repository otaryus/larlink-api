package com.agency.api.application.matching;

import java.util.Set;
import java.util.UUID;

public record MatchProfile(
        UUID id,
        UUID accountId,
        String name,
        String city,
        String province,
        String district,
        String neighborhood,
        Set<String> skills,
        Set<String> availability,
        int expectedSalary,
        int yearsOfExperience,
        String status,
        boolean employed,
        long completedContracts) {
}
