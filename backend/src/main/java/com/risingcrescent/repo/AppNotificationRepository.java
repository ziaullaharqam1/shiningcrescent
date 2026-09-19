package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findByRecipientUsernameOrderByCreatedAtDesc(String username);

    long countByRecipientUsernameAndReadFlagFalse(String username);
}
