package com.agency.api.repository;
import com.agency.api.domain.FinanceAdjustment; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface FinanceAdjustmentRepository extends JpaRepository<FinanceAdjustment,UUID> { List<FinanceAdjustment> findByFinancialCloseIdOrderByCreatedAtAsc(UUID closeId); }
