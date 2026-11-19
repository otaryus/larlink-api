package com.agency.api.repository;
import com.agency.api.domain.UserNotification; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface UserNotificationRepository extends JpaRepository<UserNotification,UUID> { List<UserNotification> findByAccountIdOrderByCreatedAtDesc(UUID id); }
