package com.familyfund.notification.internal.repository;

import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.internal.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA repository for the personal inbox (table `notifications`, US-39).
 *
 * <p>Every query is filtered by `recipient_account_id` + `clan_id`: the inbox is per-account, and the tenant
 * column keeps a notification from leaking across clans even if an account id is reused in a test.</p>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    Optional<Notification> findByNotificationIdAndRecipientAccountId(String notificationId, String recipientAccountId);

    Page<Notification> findByRecipientAccountIdAndClanIdOrderByCreatedAtDesc(
            String recipientAccountId, String clanId, Pageable pageable);

    Page<Notification> findByRecipientAccountIdAndClanIdAndCategoryOrderByCreatedAtDesc(
            String recipientAccountId, String clanId, NotificationCategory category, Pageable pageable);

    Page<Notification> findByRecipientAccountIdAndClanIdAndIsReadOrderByCreatedAtDesc(
            String recipientAccountId, String clanId, Boolean isRead, Pageable pageable);

    Page<Notification> findByRecipientAccountIdAndClanIdAndCategoryAndIsReadOrderByCreatedAtDesc(
            String recipientAccountId, String clanId, NotificationCategory category, Boolean isRead,
            Pageable pageable);

    long countByRecipientAccountIdAndClanIdAndIsReadFalse(String recipientAccountId, String clanId);

    long countByRecipientAccountIdAndClanIdAndCategoryAndIsReadFalse(
            String recipientAccountId, String clanId, NotificationCategory category);

    /**
     * Marks every unread notification of one account as read (optionally limited to one category).
     *
     * <p>Bulk update instead of loading rows: the inbox can hold thousands of rows and "mark all read" must not
     * depend on their size.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.isRead = true, n.readAt = :readAt
            WHERE n.recipientAccountId = :recipientAccountId
              AND n.clanId = :clanId
              AND n.isRead = false
            """)
    int markAllUnreadAsRead(@Param("recipientAccountId") String recipientAccountId,
                            @Param("clanId") String clanId,
                            @Param("readAt") Instant readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.isRead = true, n.readAt = :readAt
            WHERE n.recipientAccountId = :recipientAccountId
              AND n.clanId = :clanId
              AND n.category = :category
              AND n.isRead = false
            """)
    int markAllUnreadAsReadByCategory(@Param("recipientAccountId") String recipientAccountId,
                                      @Param("clanId") String clanId,
                                      @Param("category") NotificationCategory category,
                                      @Param("readAt") Instant readAt);
}
