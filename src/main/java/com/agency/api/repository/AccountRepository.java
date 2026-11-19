package com.agency.api.repository;
import com.agency.api.domain.Account; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AccountRepository extends JpaRepository<Account,UUID> { Optional<Account> findByEmailIgnoreCase(String email); }
