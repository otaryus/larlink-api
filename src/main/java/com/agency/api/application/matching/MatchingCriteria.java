package com.agency.api.application.matching;

import java.util.Set;

public record MatchingCriteria(
        String city,
        String district,
        String neighborhood,
        Set<String> requiredSkills,
        Set<String> requiredDays,
        Integer maximumSalary,
        String taskScope) {}
