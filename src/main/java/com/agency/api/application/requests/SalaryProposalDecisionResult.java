package com.agency.api.application.requests;

import com.agency.api.domain.EmploymentContract;
import com.agency.api.domain.SalaryProposal;

public record SalaryProposalDecisionResult(
        SalaryProposal proposal,
        String requestStatus,
        EmploymentContract contract) {
}
