package com.agency.api.repository;

import com.agency.api.domain.ContractRating;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ContractRatingRepository extends JpaRepository<ContractRating,UUID> {
    List<ContractRating> findByContractIdOrderByCreatedAtAsc(UUID contractId);
    boolean existsByContractIdAndAuthorId(UUID contractId,UUID authorId);
}
