package com.agency.api.application.matching;

import org.springframework.stereotype.Service;

@Service
public class MatchDistancePolicy {
    public static final double MAX_DISTANCE_KM = 25.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    public Result evaluate(double originLatitude, double originLongitude, double candidateLatitude, double candidateLongitude) {
        double distance = distanceKm(originLatitude, originLongitude, candidateLatitude, candidateLongitude);
        return new Result(round(distance), proximityScore(distance), isEligible(distance));
    }

    public double distanceKm(double firstLatitude, double firstLongitude, double secondLatitude, double secondLongitude) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double firstLatitudeRadians = Math.toRadians(firstLatitude);
        double secondLatitudeRadians = Math.toRadians(secondLatitude);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(firstLatitudeRadians) * Math.cos(secondLatitudeRadians) * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    public boolean isEligible(double distanceKm) {
        return distanceKm <= MAX_DISTANCE_KM;
    }

    public int proximityScore(double distanceKm) {
        if (!isEligible(distanceKm)) return 0;
        return (int) Math.round((1 - distanceKm / MAX_DISTANCE_KM) * 100);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record Result(double distanceKm, int proximityScore, boolean eligible) {}
}
