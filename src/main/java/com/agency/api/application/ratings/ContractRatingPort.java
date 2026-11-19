package com.agency.api.application.ratings;

import com.agency.api.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRatingPort {
    Optional<EmploymentContract> contract(UUID id);
    boolean paid(UUID contractId);
    List<ContractRating> ratings(UUID contractId);
    boolean alreadyRated(UUID contractId, UUID authorId);
    ContractRating save(ContractRating rating);
    void record(Account actor, String action, String entityType, UUID entityId, String details);
    void notify(UUID accountId, String type, String message, String entityType, UUID entityId);
}
