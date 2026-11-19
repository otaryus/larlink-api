package com.agency.api.repository;
import com.agency.api.domain.FinancialClose; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface FinancialCloseRepository extends JpaRepository<FinancialClose,UUID> { Optional<FinancialClose> findByContractIdAndReferenceMonth(UUID contractId,String month); }
