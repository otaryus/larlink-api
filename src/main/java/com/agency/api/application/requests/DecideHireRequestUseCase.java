package com.agency.api.application.requests;

import com.agency.api.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class DecideHireRequestUseCase {
    private final RequestDecisionPort decisions;

    public DecideHireRequestUseCase(RequestDecisionPort decisions) {
        this.decisions = decisions;
    }

    @Transactional
    public HireRequest decide(UUID requestId, UUID actorId, String status, String reason) {
        HireRequest request = decisions.lockRequest(requestId).orElseThrow(RequestNotFoundException::new);
        Account actor = decisions.findAccount(actorId).orElseThrow(RequestNotFoundException::new);
        if (!"PENDING".equals(request.getStatus())) {
            throw new RequestValidationException("Só pedidos pendentes podem ser decididos.");
        }
        if (!Set.of("APPROVED", "REJECTED").contains(status)) {
            throw new RequestValidationException("Decisão inválida.");
        }
        if ("REJECTED".equals(status) && (reason == null || reason.trim().length() < 10)) {
            throw new RequestValidationException("O motivo deve ter pelo menos 10 caracteres.");
        }
        request.decide(status, reason);
        decisions.record(actor, "HIRE_REQUEST_" + status, "HireRequest", requestId, reason);
        decisions.notify(request.getClient().getId(), "HIRE_REQUEST_REVIEWED",
                "A agência actualizou o estado do seu pedido.", "HireRequest", requestId);
        decisions.notify(request.getHousekeeper().getAccount().getId(), "HIRE_REQUEST_REVIEWED",
                "A agência analisou o pedido de contratação.", "HireRequest", requestId);
        return request;
    }
}
