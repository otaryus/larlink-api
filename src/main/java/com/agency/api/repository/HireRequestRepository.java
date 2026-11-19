package com.agency.api.repository;
import com.agency.api.domain.HireRequest; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.util.*;
public interface HireRequestRepository extends JpaRepository<HireRequest,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from HireRequest r where r.id=:id") Optional<HireRequest> findLockedById(@Param("id") UUID id);
    @Query("select r from HireRequest r where r.client.id=:id order by r.createdAt desc") List<HireRequest> findByClientIdOrderByCreatedAtDesc(@Param("id") UUID id);
    @Query("select r from HireRequest r where r.housekeeper.account.id=:id order by r.createdAt desc") List<HireRequest> findByWorkerAccountId(@Param("id") UUID id);
    List<HireRequest> findAllByOrderByCreatedAtDesc();
    @Query("select (count(r)>0) from HireRequest r where r.client.id=:clientId and r.housekeeper.id=:workerId and r.status=:status") boolean existsByClientIdAndHousekeeperIdAndStatus(@Param("clientId") UUID clientId,@Param("workerId") UUID housekeeperId,@Param("status") String status);
    @Query("select (count(r)>0) from HireRequest r where r.client.id=:clientId and r.housekeeper.id=:workerId and r.status in :statuses") boolean existsByClientIdAndHousekeeperIdAndStatusIn(@Param("clientId") UUID clientId,@Param("workerId") UUID housekeeperId,@Param("statuses") Set<String> statuses);
}
