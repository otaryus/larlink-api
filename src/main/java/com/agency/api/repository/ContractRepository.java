package com.agency.api.repository;
import com.agency.api.domain.EmploymentContract; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.util.*;
public interface ContractRepository extends JpaRepository<EmploymentContract,UUID> {
    @Query("select c from EmploymentContract c where c.hireRequest.client.id=:id order by c.createdAt desc") List<EmploymentContract> findForClient(@Param("id") UUID id);
    @Query("select c from EmploymentContract c where c.hireRequest.housekeeper.account.id=:id order by c.createdAt desc") List<EmploymentContract> findForWorker(@Param("id") UUID id);
    List<EmploymentContract> findAllByOrderByCreatedAtDesc();
    boolean existsByHireRequestId(UUID requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from EmploymentContract c where c.id=:id") Optional<EmploymentContract> findLockedById(@Param("id") UUID id);
}
