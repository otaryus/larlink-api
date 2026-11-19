package com.agency.api.repository;
import com.agency.api.domain.IncidentReport; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface IncidentRepository extends JpaRepository<IncidentReport,UUID> {
    List<IncidentReport> findAllByOrderByCreatedAtDesc();
}
