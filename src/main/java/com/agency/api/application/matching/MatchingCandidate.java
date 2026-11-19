package com.agency.api.application.matching;

import java.util.Set;
import java.util.UUID;

public record MatchingCandidate(
        UUID id,
        String city,
        String district,
        String neighborhood,
        Set<String> skills,
        Set<String> availability,
        int expectedSalary,
        int yearsOfExperience) {}
