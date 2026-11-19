package com.agency.api.adapters.ai;

import com.agency.api.application.matching.MatchRankingPort;
import com.agency.api.application.matching.MatchingCandidate;
import com.agency.api.application.matching.MatchingCriteria;
import com.agency.api.service.AiHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.*;

@Service
public class DeepSeekMatchRankingAdapter implements MatchRankingPort {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekMatchRankingAdapter.class);
    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final ObjectMapper mapper;
    private final AiHttpClient http;

    public DeepSeekMatchRankingAdapter(ObjectMapper mapper, AiHttpClient http,
                                       @Value("${app.matching.ai.api-key:}") String apiKey,
                                       @Value("${app.matching.ai.model:deepseek-chat}") String model,
                                       @Value("${app.matching.ai.base-url:https://api.deepseek.com}") String baseUrl) {
        this.mapper = mapper;
        this.http = http;
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
    }

    @Override
    public Map<UUID, String> rank(List<MatchingCandidate> candidates, MatchingCriteria criteria) {
        if (apiKey.isBlank() || candidates.isEmpty()) return Map.of();
        try {
            List<Map<String, Object>> candidateData = candidates.stream().map(candidate -> Map.<String, Object>of(
                    "id", candidate.id().toString(),
                    "city", candidate.city(),
                    "district", candidate.district(),
                    "neighborhood", candidate.neighborhood(),
                    "skills", candidate.skills(),
                    "availability", candidate.availability(),
                    "expectedSalary", candidate.expectedSalary(),
                    "yearsOfExperience", candidate.yearsOfExperience())).toList();
            Map<String, Object> criteriaData = new LinkedHashMap<>();
            criteriaData.put("city", criteria.city());
            criteriaData.put("district", criteria.district());
            criteriaData.put("neighborhood", criteria.neighborhood());
            criteriaData.put("requiredSkills", criteria.requiredSkills());
            criteriaData.put("requiredDays", criteria.requiredDays());
            criteriaData.put("maximumSalaryMzn", criteria.maximumSalary());
            criteriaData.put("taskScope", criteria.taskScope());
            String instructions = "Produza JSON válido. Ordene perfis elegíveis para uma pesquisa de trabalhadora doméstica. Os perfis enviados já cumprem o raio máximo de 25 km, competências, dias e limite salarial; nunca relaxe esses requisitos. Dê preferência a menor distância, experiência, bairro, escopo e proximidade salarial. Não exija distrito exacto e não infira atributos pessoais. Responda exactamente no formato {\"matches\":[{\"id\":\"UUID\",\"reason\":\"motivo curto em português\"}]}. Inclua cada id recebido uma única vez e não invente ids.";
            int maxTokens = Math.min(8192, Math.max(1200, candidates.size() * 100));
            Map<String, Object> body = Map.of("model", model, "temperature", 0, "max_tokens", maxTokens,
                    "response_format", Map.of("type", "json_object"), "messages", List.of(
                            Map.of("role", "system", "content", instructions),
                            Map.of("role", "user", "content", mapper.writeValueAsString(Map.of("criteria", criteriaData, "candidates", candidateData)))));
            var response = http.post(endpoint, Duration.ofSeconds(18),
                    Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json"),
                    mapper.writeValueAsString(body));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("DeepSeek API returned " + response.statusCode());
            JsonNode root = mapper.readTree(response.body());
            String output = root.path("choices").path(0).path("message").path("content").asText();
            if (output.isBlank()) throw new IllegalStateException("DeepSeek returned an empty JSON response");
            JsonNode matches = mapper.readTree(output).path("matches");
            if (!matches.isArray()) throw new IllegalStateException("DeepSeek response did not contain a matches array");
            Set<UUID> allowed = new HashSet<>();
            candidates.forEach(candidate -> allowed.add(candidate.id()));
            Map<UUID, String> result = new LinkedHashMap<>();
            for (JsonNode match : matches) {
                try {
                    UUID id = UUID.fromString(match.path("id").asText());
                    if (allowed.contains(id)) {
                        String reason = match.path("reason").asText();
                        result.putIfAbsent(id, reason.isBlank() ? "Compatibilidade com os critérios indicados." : reason);
                    }
                } catch (IllegalArgumentException ignored) {
                    log.debug("Ignoring invalid candidate id returned by DeepSeek");
                }
            }
            if (result.isEmpty()) throw new IllegalStateException("DeepSeek did not return any eligible candidate ids");
            if (result.size() < candidates.size()) {
                log.info("DeepSeek returned a partial ranking: ranked={} eligible={}", result.size(), candidates.size());
            }
            return result;
        } catch (Exception e) {
            log.warn("DeepSeek matching unavailable; using deterministic ranking: {}", e.getMessage());
            return Map.of();
        }
    }
}
