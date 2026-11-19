package com.agency.api.adapters.persistence;

import com.agency.api.application.matching.MatchProfile;
import com.agency.api.application.matching.MatchProfileSourcePort;
import com.agency.api.domain.EmploymentContract;
import com.agency.api.repository.ContractRepository;
import com.agency.api.repository.HousekeeperRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MatchingProfileSourceAdapter implements MatchProfileSourcePort {
    private final HousekeeperRepository housekeepers;
    private final ContractRepository contracts;

    public MatchingProfileSourceAdapter(HousekeeperRepository housekeepers, ContractRepository contracts) {
        this.housekeepers = housekeepers;
        this.contracts = contracts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchProfile> findAll() {
        List<EmploymentContract> allContracts = contracts.findAll();
        Set<UUID> employedIds = allContracts.stream()
                .filter(contract -> "ACTIVE".equals(contract.getStatus()))
                .map(contract -> contract.getHireRequest().getHousekeeper().getId())
                .collect(Collectors.toSet());
        Map<UUID, Long> completedContractCounts = allContracts.stream()
                .filter(contract -> "TERMINATED".equals(contract.getStatus()) || "ACTIVE".equals(contract.getStatus()))
                .collect(Collectors.groupingBy(
                        contract -> contract.getHireRequest().getHousekeeper().getId(),
                        Collectors.counting()));

        return housekeepers.findAll().stream()
                .map(profile -> new MatchProfile(
                        profile.getId(),
                        profile.getAccount().getId(),
                        profile.getName(),
                        profile.getCity(),
                        profile.getProvince(),
                        profile.getDistrict(),
                        profile.getNeighborhood(),
                        Set.copyOf(profile.getSkills()),
                        Set.copyOf(profile.getAvailability()),
                        profile.getExpectedSalary(),
                        profile.getYearsOfExperience(),
                        profile.getStatus(),
                        employedIds.contains(profile.getId()),
                        completedContractCounts.getOrDefault(profile.getId(), 0L)))
                .toList();
    }
}
