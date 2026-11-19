package com.agency.api.repository;
import com.agency.api.domain.ContractRevision; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ContractRevisionRepository extends JpaRepository<ContractRevision,UUID> { List<ContractRevision> findByContractIdOrderByRevisionNumberDesc(UUID id); Optional<ContractRevision> findFirstByContractIdOrderByRevisionNumberDesc(UUID id); }
