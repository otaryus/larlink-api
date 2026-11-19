package com.agency.api.adapters.persistence;

import com.agency.api.application.ratings.ContractRatingPort;
import com.agency.api.domain.*;
import com.agency.api.repository.*;
import com.agency.api.service.AuditService;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ContractRatingPersistenceAdapter implements ContractRatingPort {
    private final ContractRepository contracts;
    private final PaymentObligationRepository payments;
    private final ContractRatingRepository ratings;
    private final AuditService audit;

    public ContractRatingPersistenceAdapter(ContractRepository contracts, PaymentObligationRepository payments,
            ContractRatingRepository ratings, AuditService audit) {
        this.contracts = contracts;
        this.payments = payments;
        this.ratings = ratings;
        this.audit = audit;
    }

    @Override public Optional<EmploymentContract> contract(UUID id) { return contracts.findById(id); }
    @Override public boolean paid(UUID id) { return payments.existsByContractIdAndStatus(id, "PAGO"); }
    @Override public List<ContractRating> ratings(UUID id) { return ratings.findByContractIdOrderByCreatedAtAsc(id); }
    @Override public boolean alreadyRated(UUID contractId, UUID authorId) {
        return ratings.existsByContractIdAndAuthorId(contractId, authorId);
    }
    @Override public ContractRating save(ContractRating rating) { return ratings.save(rating); }
    @Override public void record(Account actor, String action, String entityType, UUID entityId, String details) {
        audit.record(actor, action, entityType, entityId, details);
    }
    @Override public void notify(UUID accountId, String type, String message, String entityType, UUID entityId) {
        audit.notify(accountId, type, message, entityType, entityId);
    }
}
