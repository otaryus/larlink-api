package com.agency.api.application.matching;

import java.util.Set;

public record SearchMatchesQuery(
        String city,
        String district,
        String neighborhood,
        Set<String> skills,
        Set<String> availability,
        Integer maxSalary,
        String text) {
}
