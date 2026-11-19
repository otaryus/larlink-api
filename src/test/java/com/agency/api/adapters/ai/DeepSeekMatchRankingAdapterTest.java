package com.agency.api.adapters.ai;

import com.agency.api.application.matching.MatchingCandidate;
import com.agency.api.application.matching.MatchingCriteria;
import com.agency.api.service.AiHttpClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekMatchRankingAdapterTest {

    private static final class FakeClient implements AiHttpClient {
        URI uri;
        Map<String, String> headers;
        String requestBody;
        Response response;
        Exception error;

        @Override
        public Response post(URI uri, Duration timeout, Map<String, String> headers, String body) throws Exception {
            this.uri = uri;
            this.headers = headers;
            this.requestBody = body;
            if (error != null) throw error;
            return response;
        }
    }

    private MatchingCandidate candidate(int salary, int years) {
        return new MatchingCandidate(UUID.randomUUID(), "Cidade de Maputo", "KaMpfumu", "Polana",
                Set.of("CLEANING"), Set.of("MON"), salary, years);
    }

    private MatchingCriteria criteria() {
        return new MatchingCriteria("Cidade de Maputo", "KaMpfumu", "Polana",
                Set.of("CLEANING"), Set.of("MON"), 10_000, "Limpeza geral");
    }

    @Test
    void ranksWithJsonModeAndPreservesReturnedOrderWithoutSendingPersonalDetails() throws Exception {
        var mapper = new ObjectMapper();
        var http = new FakeClient();
        var first = candidate(10_000, 4);
        var second = candidate(9_500, 7);
        String ranked = mapper.writeValueAsString(Map.of("matches", List.of(
                Map.of("id", second.id().toString(), "reason", "Maior experiência"),
                Map.of("id", first.id().toString(), "reason", "Boa compatibilidade"))));
        http.response = new AiHttpClient.Response(200, mapper.writeValueAsString(
                Map.of("choices", List.of(Map.of("message", Map.of("content", ranked))))));
        var adapter = new DeepSeekMatchRankingAdapter(mapper, http, "secret-key", "deepseek-chat", "https://api.deepseek.com/");

        var result = adapter.rank(List.of(first, second), criteria());

        assertEquals(List.of(second.id(), first.id()), new ArrayList<>(result.keySet()));
        assertEquals(URI.create("https://api.deepseek.com/chat/completions"), http.uri);
        assertEquals("Bearer secret-key", http.headers.get("Authorization"));
        var request = mapper.readTree(http.requestBody);
        assertEquals("deepseek-chat", request.path("model").asText());
        assertEquals("json_object", request.path("response_format").path("type").asText());
        assertFalse(http.requestBody.contains("email"));
        assertFalse(http.requestBody.contains("phone"));
    }

    @Test
    void fallsBackWhenDeepSeekIsUnavailableOrReturnsUnknownCandidates() throws Exception {
        var mapper = new ObjectMapper();
        var candidate = candidate(10_000, 4);
        var unavailable = new FakeClient();
        unavailable.error = new java.io.IOException("offline");
        assertTrue(new DeepSeekMatchRankingAdapter(mapper, unavailable, "key", "deepseek-chat", "https://api.deepseek.com")
                .rank(List.of(candidate), criteria()).isEmpty());

        var invalid = new FakeClient();
        String output = mapper.writeValueAsString(Map.of("matches", List.of(
                Map.of("id", UUID.randomUUID().toString(), "reason", "Inventado"))));
        invalid.response = new AiHttpClient.Response(200, mapper.writeValueAsString(
                Map.of("choices", List.of(Map.of("message", Map.of("content", output))))));
        assertTrue(new DeepSeekMatchRankingAdapter(mapper, invalid, "key", "deepseek-chat", "https://api.deepseek.com")
                .rank(List.of(candidate), criteria()).isEmpty());
    }

    @Test
    void keepsValidRankingWhenDeepSeekReturnsOnlySomeEligibleCandidates() throws Exception {
        var mapper = new ObjectMapper();
        var http = new FakeClient();
        var first = candidate(10_000, 4);
        var second = candidate(9_500, 7);
        String ranked = mapper.writeValueAsString(Map.of("matches", List.of(
                Map.of("id", second.id().toString(), "reason", "Experiência mais próxima"),
                Map.of("id", "invalid-id", "reason", "Resposta inválida"),
                Map.of("id", UUID.randomUUID().toString(), "reason", "Candidata não elegível"))));
        http.response = new AiHttpClient.Response(200, mapper.writeValueAsString(
                Map.of("choices", List.of(Map.of("message", Map.of("content", ranked))))));

        var result = new DeepSeekMatchRankingAdapter(mapper, http, "key", "deepseek-chat", "https://api.deepseek.com")
                .rank(List.of(first, second), criteria());

        assertEquals(List.of(second.id()), new ArrayList<>(result.keySet()));
        assertEquals("Experiência mais próxima", result.get(second.id()));
    }

    @Test
    void doesNotCallDeepSeekWithoutKeyOrCandidates() {
        var mapper = new ObjectMapper();
        var http = new FakeClient();
        var adapter = new DeepSeekMatchRankingAdapter(mapper, http, "", "deepseek-chat", "https://api.deepseek.com");

        assertTrue(adapter.rank(List.of(candidate(10_000, 4)), criteria()).isEmpty());
        assertTrue(adapter.rank(List.of(), criteria()).isEmpty());
        assertNull(http.uri);
    }
}
