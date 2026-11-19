package com.agency.api.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DemoHousekeeperCatalogTest {
    @Test
    void providesLargeDiverseAndMatchableDemoDataset() {
        var samples = DemoHousekeeperCatalog.samples();

        assertTrue(samples.size() >= 30);
        assertTrue(samples.stream().map(DemoHousekeeperCatalog.Sample::city).distinct().count() >= 10);
        assertTrue(samples.stream().map(DemoHousekeeperCatalog.Sample::district).distinct().count() >= 20);
        assertTrue(samples.stream().mapToInt(DemoHousekeeperCatalog.Sample::salary).min().orElseThrow() <= 7000);
        assertTrue(samples.stream().mapToInt(DemoHousekeeperCatalog.Sample::salary).max().orElseThrow() >= 14000);
        assertTrue(samples.stream().anyMatch(sample -> sample.days().containsAll(Set.of("MON", "TUE", "WED", "THU", "FRI"))));
        assertTrue(samples.stream().anyMatch(sample -> sample.days().contains("SUN")));
        assertTrue(samples.stream().anyMatch(sample -> sample.skills().containsAll(Set.of("CLEANING", "COOKING", "CHILDCARE"))));
        assertTrue(samples.stream().anyMatch(sample -> sample.skills().containsAll(Set.of("CLEANING", "ELDERLY_CARE"))));
        assertEquals(samples.size(), samples.stream().map(DemoHousekeeperCatalog.Sample::email).collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(samples.size(), samples.stream().map(DemoHousekeeperCatalog.Sample::phone).collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(Set.of("CLEANING", "LAUNDRY", "COOKING", "CHILDCARE", "ELDERLY_CARE", "GARDENING"),
                samples.stream().flatMap(sample -> sample.skills().stream()).collect(java.util.stream.Collectors.toCollection(HashSet::new)));
    }

    @Test
    void generatesUniqueGmailCredentialsFromFirstAndLastName() {
        var emails = DemoHousekeeperCatalog.samples().stream().map(sample -> DemoHousekeeperCatalog.emailFor(sample.name())).toList();

        assertEquals(DemoHousekeeperCatalog.samples().size(), Set.copyOf(emails).size());
        assertTrue(emails.stream().allMatch(email -> email.matches("[a-z]+@gmail\\.com")));
        assertEquals("ameliamucavele@gmail.com", DemoHousekeeperCatalog.emailFor("Amélia Mucavele"));
    }

    @Test
    void everyProfileSatisfiesMinimumMatchingConditions() {
        var locations = new com.agency.api.service.MozambiqueLocationCatalog(new ObjectMapper());

        DemoHousekeeperCatalog.samples().forEach(sample -> {
            assertFalse(sample.name().isBlank());
            assertFalse(sample.neighborhood().isBlank());
            assertTrue(locations.isValid(sample.city(), sample.district()), sample.city() + " / " + sample.district());
            assertTrue(sample.skills().contains("CLEANING"), sample.email());
            assertTrue(sample.skills().size() >= 2, sample.email());
            assertTrue(sample.days().size() >= 3, sample.email());
            assertTrue(sample.salary() >= 5000, sample.email());
            assertTrue(sample.experience() >= 3, sample.email());
        });
    }
}
