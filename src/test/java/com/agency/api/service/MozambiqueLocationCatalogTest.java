package com.agency.api.service;

import com.agency.api.application.matching.MatchDistancePolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class MozambiqueLocationCatalogTest {
    private final MozambiqueLocationCatalog catalog = new MozambiqueLocationCatalog(new ObjectMapper());

    @Test
    void resolvesCoordinatesAndDistanceAcrossDifferentMaputoDistricts() {
        var origin = catalog.coordinates("Cidade de Maputo", "KaMpfumu").orElseThrow();
        var nearby = catalog.coordinates("Cidade de Maputo", "KaMaxaqueni").orElseThrow();

        assertNotEquals(origin, nearby);
        assertTrue(new MatchDistancePolicy().distanceKm(origin.latitude(), origin.longitude(), nearby.latitude(), nearby.longitude()) < 25);
    }

    @Test
    void returnsNoCoordinatesForUnknownLocation() {
        assertTrue(catalog.coordinates("Desconhecido", "Distrito").isEmpty());
    }
}
