package com.agency.api.repository;
import com.agency.api.domain.AgencyTask; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AgencyTaskRepository extends JpaRepository<AgencyTask,UUID> { List<AgencyTask> findByActiveTrueOrderByNameAsc(); boolean existsByNameIgnoreCase(String name); }
