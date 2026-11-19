package com.agency.api.repository;
import com.agency.api.domain.IncidentMessage; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface IncidentMessageRepository extends JpaRepository<IncidentMessage,UUID> { List<IncidentMessage> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId); }
