package com.agency.api.repository;
import com.agency.api.domain.SalaryProposal; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SalaryProposalRepository extends JpaRepository<SalaryProposal,UUID> { List<SalaryProposal> findByHireRequestIdOrderByCreatedAtAsc(UUID id); }
