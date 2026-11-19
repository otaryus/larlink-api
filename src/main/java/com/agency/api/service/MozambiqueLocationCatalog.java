package com.agency.api.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class MozambiqueLocationCatalog {
    private final Map<String, List<String>> districtsByArea;
    private final Map<String, Map<String, List<Double>>> coordinatesByArea;

    public MozambiqueLocationCatalog(ObjectMapper mapper) {
        try (var stream = new ClassPathResource("db/mozambique-districts.json").getInputStream()) {
            this.districtsByArea = mapper.readValue(stream, new TypeReference<>() {});
            try (var coordinateStream = new ClassPathResource("db/mozambique-district-coordinates.json").getInputStream()) {
                this.coordinatesByArea = mapper.readValue(coordinateStream, new TypeReference<>() {});
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar a lista de distritos de Moçambique.", e);
        }
    }

    public String canonicalArea(String value) {
        if (value == null) return "";
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "maputo" -> "Cidade de Maputo";
            case "matola" -> "Província de Maputo";
            case "beira" -> "Sofala";
            case "xai-xai" -> "Gaza";
            default -> value.trim();
        };
    }

    public boolean isValid(String area, String district) {
        List<String> allowed = districtsByArea.get(canonicalArea(area));
        return allowed != null && allowed.stream().anyMatch(value -> normalize(value).equals(normalize(district)));
    }

    public Optional<Coordinates> coordinates(String area, String district) {
        String canonicalArea = canonicalArea(area);
        Map<String, List<Double>> districts = coordinatesByArea.get(canonicalArea);
        if (districts == null) return Optional.empty();
        return districts.entrySet().stream()
                .filter(entry -> normalize(entry.getKey()).equals(normalize(district)))
                .map(Map.Entry::getValue)
                .filter(values -> values.size() == 2)
                .map(values -> new Coordinates(values.get(0), values.get(1)))
                .findFirst();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
    }

    public record Coordinates(double latitude, double longitude) {}
}
