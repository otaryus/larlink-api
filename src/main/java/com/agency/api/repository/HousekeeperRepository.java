package com.agency.api.repository;
import com.agency.api.domain.HousekeeperProfile; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface HousekeeperRepository extends JpaRepository<HousekeeperProfile,UUID> { Optional<HousekeeperProfile> findByAccountId(UUID id); boolean existsByPhone(String phone); boolean existsByPhoneAndIdNot(String phone,UUID id); }
