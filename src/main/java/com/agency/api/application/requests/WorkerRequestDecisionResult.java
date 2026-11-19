package com.agency.api.application.requests;

import com.agency.api.domain.EmploymentContract;
import com.agency.api.domain.HireRequest;

public record WorkerRequestDecisionResult(HireRequest request, EmploymentContract contract) {
}
