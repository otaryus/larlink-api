package com.agency.api.repository;
import com.agency.api.domain.AuditEvent; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AuditEventRepository extends JpaRepository<AuditEvent,UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="actor")
    List<AuditEvent> findTop500ByOrderByCreatedAtDesc();
}
