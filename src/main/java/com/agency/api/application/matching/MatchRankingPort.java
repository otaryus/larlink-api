package com.agency.api.application.matching;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MatchRankingPort {
    Map<UUID, String> rank(List<MatchingCandidate> candidates, MatchingCriteria criteria);
}
