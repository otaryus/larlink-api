package com.agency.api.repository;
import com.agency.api.domain.AbsenceRecord; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AbsenceRepository extends JpaRepository<AbsenceRecord,UUID> { List<AbsenceRecord> findAllByOrderByCreatedAtDesc(); List<AbsenceRecord> findByContractHireRequestClientIdOrContractHireRequestHousekeeperAccountIdOrderByCreatedAtDesc(UUID clientId,UUID workerId); }
