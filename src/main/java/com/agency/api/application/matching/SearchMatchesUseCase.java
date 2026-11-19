package com.agency.api.application.matching;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

@Service
public class SearchMatchesUseCase {
    private final MatchProfileSourcePort profiles;
    private final MatchingLocationPort locations;
    private final MatchRankingPort ranking;
    private final MatchDistancePolicy distances;
    private final MatchScorePolicy scores;

    public SearchMatchesUseCase(
            MatchProfileSourcePort profiles,
            MatchingLocationPort locations,
            MatchRankingPort ranking,
            MatchDistancePolicy distances,
            MatchScorePolicy scores) {
        this.profiles = profiles;
        this.locations = locations;
        this.ranking = ranking;
        this.distances = distances;
        this.scores = scores;
    }

    public List<MatchingResult> search(SearchMatchesQuery query) {
        if (hasText(query.city()) && (query.district() == null || !locations.isValid(query.city(), query.district()))) {
            throw new InvalidMatchingLocationException();
        }

        List<MatchProfile> all = profiles.findAll();
        Optional<MatchingLocationPort.Coordinates> origin = locations.coordinates(query.city(), query.district());
        Map<UUID, MatchDistancePolicy.Result> distanceByProfile = new HashMap<>();
        if (origin.isPresent()) {
            MatchingLocationPort.Coordinates point = origin.get();
            for (MatchProfile profile : all) {
                locations.coordinates(profile.province(), profile.district()).ifPresent(candidate ->
                        distanceByProfile.put(profile.id(), distances.evaluate(
                                point.latitude(), point.longitude(), candidate.latitude(), candidate.longitude())));
            }
        }

        List<MatchProfile> eligible = all.stream()
                .filter(profile -> "APPROVED".equals(profile.status()))
                .filter(profile -> !profile.employed())
                .filter(profile -> !hasText(query.city())
                        || locations.canonicalArea(profile.province()).equalsIgnoreCase(locations.canonicalArea(query.city())))
                .filter(profile -> origin.isEmpty() || distanceByProfile.containsKey(profile.id())
                        && distanceByProfile.get(profile.id()).eligible())
                .filter(profile -> query.skills() == null || query.skills().isEmpty()
                        || profile.skills().containsAll(query.skills()))
                .filter(profile -> query.availability() == null || query.availability().isEmpty()
                        || profile.availability().containsAll(query.availability()))
                .filter(profile -> query.maxSalary() == null || profile.expectedSalary() <= query.maxSalary())
                .toList();

        List<MatchingCandidate> rankingCandidates = eligible.stream()
                .map(profile -> new MatchingCandidate(profile.id(), profile.city(), profile.district(), profile.neighborhood(),
                        profile.skills(), profile.availability(), profile.expectedSalary(), profile.yearsOfExperience()))
                .toList();
        MatchingCriteria criteria = new MatchingCriteria(query.city(), query.district(), query.neighborhood(),
                query.skills() == null ? Set.of() : Set.copyOf(query.skills()),
                query.availability() == null ? Set.of() : Set.copyOf(query.availability()),
                query.maxSalary(), query.text());
        Map<UUID, String> aiOrder = ranking.rank(rankingCandidates, criteria);
        Map<UUID, Integer> aiPositions = new HashMap<>();
        int position = 0;
        for (UUID id : aiOrder.keySet()) aiPositions.put(id, position++);

        Map<UUID, MatchScorePolicy.Score> scoreByProfile = new HashMap<>();
        for (MatchProfile profile : eligible) {
            scoreByProfile.put(profile.id(), scores.combine(
                    deterministicScore(profile, query, distanceByProfile.get(profile.id())),
                    aiPositions.get(profile.id()), eligible.size()));
        }

        return eligible.stream()
                .sorted(Comparator.comparingInt((MatchProfile profile) -> scoreByProfile.get(profile.id()).total()).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (MatchProfile profile) -> scoreByProfile.get(profile.id()).deterministic()).reversed())
                        .thenComparing(Comparator.comparingInt(MatchProfile::yearsOfExperience).reversed()))
                .map(profile -> toResult(profile, query, distanceByProfile.get(profile.id()),
                        scoreByProfile.get(profile.id()), aiOrder))
                .toList();
    }

    private MatchingResult toResult(
            MatchProfile profile,
            SearchMatchesQuery query,
            MatchDistancePolicy.Result distance,
            MatchScorePolicy.Score score,
            Map<UUID, String> aiOrder) {
        Set<String> requestedSkills = query.skills() == null ? Set.of() : query.skills();
        Set<String> requestedDays = query.availability() == null ? Set.of() : query.availability();
        List<String> missingSkills = requestedSkills.stream().filter(skill -> !profile.skills().contains(skill)).sorted().toList();
        List<String> missingDays = requestedDays.stream().filter(day -> !profile.availability().contains(day)).sorted().toList();
        List<String> reasons = new ArrayList<>();
        if (!requestedSkills.isEmpty()) reasons.add((requestedSkills.size() - missingSkills.size()) + "/" + requestedSkills.size() + " competências pedidas");
        if (!requestedDays.isEmpty()) reasons.add((requestedDays.size() - missingDays.size()) + "/" + requestedDays.size() + " dias disponíveis");
        if (hasText(query.district()) && !profile.district().equalsIgnoreCase(query.district())) reasons.add("Distrito diferente: " + profile.district());
        if (hasText(query.neighborhood()) && !sameNeighborhood(profile.neighborhood(), query.neighborhood())) reasons.add("Bairro diferente: " + profile.neighborhood());
        if (query.maxSalary() != null && profile.expectedSalary() > query.maxSalary()) {
            reasons.add("Salário pretendido acima do orçamento em "
                    + (profile.expectedSalary() - query.maxSalary()) + " MZN");
        }

        String reason = reasons.isEmpty() ? "Cumpre os critérios indicados." : String.join(" · ", reasons);
        if (!aiOrder.isEmpty()) reason = aiOrder.getOrDefault(profile.id(), reason);
        return new MatchingResult(
                profile.id(), profile.accountId(), profile.name(), profile.city(), profile.district(), profile.neighborhood(),
                profile.skills(), profile.availability(), profile.expectedSalary(), profile.yearsOfExperience(), profile.status(),
                score.total(), score.deterministic(), score.ai(), score.deterministicWeight(), score.aiWeight(),
                distance == null ? null : distance.distanceKm(),
                distance == null ? null : distance.proximityScore(), MatchDistancePolicy.MAX_DISTANCE_KM,
                missingSkills, missingDays, true, reason, profile.completedContracts(),
                profile.yearsOfExperience() >= 5
                        ? "Perfil aprovado pela agência com experiência confirmada"
                        : "Perfil aprovado pela agência");
    }

    private int deterministicScore(
            MatchProfile profile,
            SearchMatchesQuery query,
            MatchDistancePolicy.Result distance) {
        double points = 0;
        int possible = 0;
        if (query.skills() != null && !query.skills().isEmpty()) {
            possible += 40;
            long matched = query.skills().stream().filter(profile.skills()::contains).count();
            points += 40.0 * matched / query.skills().size();
        }
        if (query.availability() != null && !query.availability().isEmpty()) {
            possible += 25;
            long matched = query.availability().stream().filter(profile.availability()::contains).count();
            points += 25.0 * matched / query.availability().size();
        }
        if (hasText(query.district())) {
            possible += 25;
            if (distance != null) points += 25.0 * distance.proximityScore() / 100.0;
        }
        if (hasText(query.neighborhood())) {
            possible += 5;
            String actual = normalize(profile.neighborhood());
            String desired = normalize(query.neighborhood());
            if (actual.equals(desired)) points += 5;
            else if (actual.contains(desired) || desired.contains(actual)) points += 4;
            else points += 1;
        }
        if (query.maxSalary() != null && query.maxSalary() > 0) {
            possible += 5;
            int over = Math.max(0, profile.expectedSalary() - query.maxSalary());
            points += 5.0 * Math.max(0, 1.0 - (double) over / query.maxSalary());
        }
        return possible == 0 ? 0 : (int) Math.round(points * 100.0 / possible);
    }

    private boolean sameNeighborhood(String actual, String desired) {
        String normalizedActual = normalize(actual);
        String normalizedDesired = normalize(desired);
        return normalizedActual.equals(normalizedDesired)
                || normalizedActual.contains(normalizedDesired) || normalizedDesired.contains(normalizedActual);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
