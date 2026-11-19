package com.agency.api.application.matching;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SearchMatchesUseCaseTest {
    private final UUID requestedId = UUID.randomUUID();
    private final UUID outsideRadiusId = UUID.randomUUID();
    private final UUID employedId = UUID.randomUUID();
    private final UUID unapprovedId = UUID.randomUUID();
    private final TestProfileSource profiles = new TestProfileSource(List.of(
            profile(requestedId, "APPROVED", false, 1, "KaMpfumu", Set.of("cleaning"), Set.of("MONDAY"), 8000, 4, "KaMpfumu"),
            profile(outsideRadiusId, "APPROVED", false, 0, "Matola", Set.of("cleaning"), Set.of("MONDAY"), 7000, 2, "Matola"),
            profile(employedId, "APPROVED", true, 3, "KaMpfumu", Set.of("cleaning"), Set.of("MONDAY"), 7000, 8, "KaMpfumu"),
            profile(unapprovedId, "PENDING", false, 0, "KaMpfumu", Set.of("cleaning"), Set.of("MONDAY"), 7000, 5, "KaMpfumu")));
    private final TestLocations locations = new TestLocations();
    private final TestRanking ranking = new TestRanking();
    private final SearchMatchesUseCase useCase = new SearchMatchesUseCase(
            profiles, locations, ranking, new MatchDistancePolicy(), new MatchScorePolicy());

    @Test
    void filtersHardRequirementsBeforeRankingAndReturnsExistingMatchFields() {
        ranking.result = Map.of(requestedId, "Boa correspondência");

        List<MatchingResult> results = useCase.search(new SearchMatchesQuery(
                "Cidade de Maputo", "KaMpfumu", "Polana", Set.of("cleaning"),
                Set.of("MONDAY"), 9000, "trabalho diário"));

        assertEquals(1, results.size());
        MatchingResult result = results.getFirst();
        assertEquals(requestedId, result.id());
        assertEquals("Boa correspondência", result.matchReason());
        assertEquals(1, result.completedContracts());
        assertTrue(result.canRequest());
        assertEquals(List.of(), result.missingSkills());
        assertEquals(25.0, result.maximumDistanceKm());
        assertTrue(result.distanceKm() < 25.0);
        assertEquals(1, ranking.candidates.size());
        assertEquals(requestedId, ranking.candidates.getFirst().id());
    }

    @Test
    void rejectsInvalidLocationBeforeLoadingCandidates() {
        assertThrows(InvalidMatchingLocationException.class, () -> useCase.search(
                new SearchMatchesQuery("Cidade de Maputo", "Not a district", null, null, null, null, null)));
        assertFalse(profiles.loaded);
    }

    @Test
    void keepsSearchWithoutLocationAndUsesDeterministicScoreWhenAiHasNoRanking() {
        List<MatchingResult> results = useCase.search(new SearchMatchesQuery(
                null, null, null, null, null, null, null));

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(result -> "Cumpre os critérios indicados.".equals(result.matchReason())));
        assertTrue(results.stream().allMatch(result -> result.distanceKm() == null));
        assertTrue(results.stream().allMatch(result -> result.aiWeight() == 0));
    }

    private static MatchProfile profile(UUID id, String status, boolean employed, long completed,
                                        String district, Set<String> skills, Set<String> days,
                                        int salary, int years, String neighborhood) {
        return new MatchProfile(id, UUID.randomUUID(), id.toString(), "Cidade de Maputo",
                "Cidade de Maputo", district, neighborhood, skills, days, salary, years,
                status, employed, completed);
    }

    private static final class TestProfileSource implements MatchProfileSourcePort {
        private final List<MatchProfile> values;
        private boolean loaded;

        private TestProfileSource(List<MatchProfile> values) {
            this.values = values;
        }

        @Override
        public List<MatchProfile> findAll() {
            loaded = true;
            return values;
        }
    }

    private static final class TestLocations implements MatchingLocationPort {
        @Override
        public boolean isValid(String area, String district) {
            return "KaMpfumu".equalsIgnoreCase(district);
        }

        @Override
        public String canonicalArea(String area) {
            return area == null ? "" : area;
        }

        @Override
        public Optional<Coordinates> coordinates(String area, String district) {
            if ("KaMpfumu".equalsIgnoreCase(district)) return Optional.of(new Coordinates(-25.96, 32.58));
            if ("Matola".equalsIgnoreCase(district)) return Optional.of(new Coordinates(-25.97, 32.20));
            return Optional.empty();
        }
    }

    private static final class TestRanking implements MatchRankingPort {
        private Map<UUID, String> result = Map.of();
        private List<MatchingCandidate> candidates = List.of();

        @Override
        public Map<UUID, String> rank(List<MatchingCandidate> candidates, MatchingCriteria criteria) {
            this.candidates = candidates;
            return result;
        }
    }
}
