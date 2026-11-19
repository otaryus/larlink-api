package com.agency.api.service;

import com.agency.api.domain.*;
import com.agency.api.repository.*;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ContractRatingService {
    private final ContractRepository contracts;
    private final ContractRatingRepository ratings;
    private final PaymentObligationRepository payments;
    public ContractRatingService(ContractRepository contracts,ContractRatingRepository ratings,PaymentObligationRepository payments){this.contracts=contracts;this.ratings=ratings;this.payments=payments;}

    public EmploymentContract requirePartyContract(UUID id,Account actor){
        var contract=contracts.findById(id).orElseThrow(()->new NoSuchElementException("Contrato não encontrado."));
        var request=contract.getHireRequest();
        if(!request.getClient().getId().equals(actor.getId())&&!request.getHousekeeper().getAccount().getId().equals(actor.getId()))throw new SecurityException("Este contrato não pertence à sua conta.");
        return contract;
    }
    public boolean isEligible(EmploymentContract c){return "TERMINATED".equals(c.getStatus())||payments.existsByContractIdAndStatus(c.getId(),"PAGO");}
    public ContractRating create(EmploymentContract contract,Account actor,int score,String comment){
        if(score<1||score>5)throw new IllegalArgumentException("A avaliação deve ser entre 1 e 5 estrelas.");
        if(!isEligible(contract))throw new IllegalStateException("A avaliação fica disponível após um pagamento confirmado ou o encerramento do contrato.");
        if(ratings.existsByContractIdAndAuthorId(contract.getId(),actor.getId()))throw new IllegalStateException("Já enviou uma avaliação para este contrato.");
        return ratings.save(new ContractRating(contract,actor,score,comment==null?null:comment.trim()));
    }
    public List<ContractRating> list(EmploymentContract contract){return ratings.findByContractIdOrderByCreatedAtAsc(contract.getId());}
}
