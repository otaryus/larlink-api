package com.agency.api.application.ratings;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class ContractRatingUseCase {
    private final ContractRatingPort ratings;

    public ContractRatingUseCase(ContractRatingPort ratings) { this.ratings = ratings; }

    @Transactional(readOnly = true)
    public ContractRatingDetails list(UUID contractId, UUID actorId) {
        EmploymentContract contract = partyContract(contractId, actorId);
        List<ContractRating> all = ratings.ratings(contractId);
        double average = all.stream().mapToInt(ContractRating::getScore).average().orElse(0);
        return new ContractRatingDetails(eligible(contract), all, average,
                all.stream().anyMatch(rating -> rating.getAuthor().getId().equals(actorId)));
    }

    @Transactional(noRollbackFor = {ContractRatingNotFoundException.class, ContractRatingForbiddenException.class,
            ContractRatingValidationException.class})
    public ContractRating create(UUID contractId, Account actor, int score, String comment) {
        EmploymentContract contract = partyContract(contractId, actor.getId());
        if (score < 1 || score > 5)
            throw new ContractRatingValidationException("A avaliação deve ser entre 1 e 5 estrelas.");
        if (!eligible(contract))
            throw new ContractRatingValidationException(
                    "A avaliação fica disponível após um pagamento confirmado ou o encerramento do contrato.");
        if (ratings.alreadyRated(contractId, actor.getId()))
            throw new ContractRatingValidationException("Já enviou uma avaliação para este contrato.");
        ContractRating saved = ratings.save(new ContractRating(contract, actor, score,
                comment == null ? null : comment.trim()));
        ratings.record(actor, "CONTRACT_RATED", "ContractRating", saved.getId(),
                "Classificação " + saved.getScore() + "/5");
        Account other = actor.getId().equals(contract.getHireRequest().getClient().getId())
                ? contract.getHireRequest().getHousekeeper().getAccount()
                : contract.getHireRequest().getClient();
        ratings.notify(other.getId(), "CONTRACT_RATING",
                "Recebeu uma avaliação sobre um contrato concluído.", "ContractRating", saved.getId());
        return saved;
    }

    private EmploymentContract partyContract(UUID id, UUID actorId) {
        EmploymentContract contract = ratings.contract(id).orElseThrow(ContractRatingNotFoundException::new);
        HireRequest request = contract.getHireRequest();
        if (!request.getClient().getId().equals(actorId)
                && !request.getHousekeeper().getAccount().getId().equals(actorId))
            throw new ContractRatingForbiddenException("Este contrato não pertence à sua conta.");
        return contract;
    }

    private boolean eligible(EmploymentContract contract) {
        return "TERMINATED".equals(contract.getStatus()) || ratings.paid(contract.getId());
    }
}
