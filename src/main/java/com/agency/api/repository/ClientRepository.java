package com.agency.api.repository;
import com.agency.api.domain.ClientProfile; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ClientRepository extends JpaRepository<ClientProfile,UUID> { Optional<ClientProfile> findByAccountId(UUID id); }
