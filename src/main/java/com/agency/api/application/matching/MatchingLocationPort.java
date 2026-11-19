package com.agency.api.application.matching;

import java.util.Optional;

public interface MatchingLocationPort {
    boolean isValid(String area, String district);

    String canonicalArea(String area);

    Optional<Coordinates> coordinates(String area, String district);

    record Coordinates(double latitude, double longitude) {
    }
}
