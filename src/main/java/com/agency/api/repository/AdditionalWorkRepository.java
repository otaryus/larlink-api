package com.agency.api.repository;
import com.agency.api.domain.AdditionalWork; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AdditionalWorkRepository extends JpaRepository<AdditionalWork,UUID> { List<AdditionalWork> findAllByOrderByCreatedAtDesc(); List<AdditionalWork> findByContractHireRequestClientIdOrContractHireRequestHousekeeperAccountIdOrderByCreatedAtDesc(UUID clientId,UUID workerId); }
