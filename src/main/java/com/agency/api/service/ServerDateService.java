package com.agency.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agency.api.repository.AgencyFinancePolicyRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class ServerDateService {
    private static final ZoneId MOZAMBIQUE_TIME_ZONE = ZoneId.of("Africa/Maputo");

    private final AgencyFinancePolicyRepository policies;
    private final boolean testDateEnabled;

    public ServerDateService(AgencyFinancePolicyRepository policies,
                             @Value("${app.testing-date.enabled:false}") boolean testDateEnabled) {
        this.policies = policies;
        this.testDateEnabled = testDateEnabled;
    }

    public LocalDate currentDate() {
        if (testDateEnabled) {
            return policies.findById("agency")
                    .map(policy -> policy.getTestDateOverride())
                    .orElseGet(() -> LocalDate.now(MOZAMBIQUE_TIME_ZONE));
        }
        return LocalDate.now(MOZAMBIQUE_TIME_ZONE);
    }

    public boolean isTestDateEnabled() {
        return testDateEnabled;
    }
}
