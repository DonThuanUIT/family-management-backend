package com.familyfund.notification.internal.repository;

import com.familyfund.notification.internal.entity.NotificationDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for `notification_device_tokens` (US-39): the FCM tokens push is delivered to.
 */
@Repository
public interface NotificationDeviceTokenRepository extends JpaRepository<NotificationDeviceToken, String> {

    /** `fcm_token` is UNIQUE, so a device maps to at most one row whatever account registered it last. */
    Optional<NotificationDeviceToken> findByFcmToken(String fcmToken);

    /** Active tokens of one account, used when pushing a notification. */
    @Query("""
            SELECT t.fcmToken FROM NotificationDeviceToken t
            WHERE t.accountId = :accountId AND t.isActive = true
            ORDER BY t.createdAt DESC
            """)
    List<String> findActiveTokens(@Param("accountId") String accountId);
}
