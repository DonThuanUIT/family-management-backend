package com.familyfund.notification.api;

import com.familyfund.notification.api.dto.NotificationResponse;
import com.familyfund.notification.api.dto.UnreadCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public contract of the notification module (US-39).
 *
 * <p>Two responsibilities:</p>
 * <ol>
 *   <li><b>Write path</b> — {@link #notify(NotificationCommand)} persists one row in {@code notifications} for one
 *       recipient account and pushes it to that account's active FCM device tokens.</li>
 *   <li><b>Read path</b> — the personal inbox: paged listing with category/read filters, unread counters, and
 *       marking notifications as read ({@code is_read = true}). Every read/write of the inbox is scoped to the
 *       authenticated caller's own account and the current tenant, so one account can never see or mutate
 *       another account's notifications.</li>
 * </ol>
 *
 * <p>Device tokens are registered by the mobile/web client through
 * {@link #registerDeviceToken(String, String)} and removed on logout.</p>
 */
public interface NotificationService {

    /**
     * Persists one notification and pushes it to the recipient's devices.
     *
     * <p>The row is saved before the push is attempted; a push failure never rolls the notification back
     * (the user still sees it in the inbox), and invalid FCM tokens are deactivated instead of failing the call.</p>
     *
     * @param command recipient, category, title, content and optional business reference
     * @return the generated {@code notifications.notification_id}
     * @throws com.familyfund.core.exception.BadRequestException when the command is incomplete
     */
    String notify(NotificationCommand command);

    /**
     * Lists the caller's notifications, newest first.
     *
     * @param category optional category filter (Event / Task / Finance)
     * @param isRead   optional read-state filter ({@code true} = read, {@code false} = unread)
     * @param pageable pagination (defaults are applied by the controller)
     * @return page of notifications belonging to the caller
     */
    Page<NotificationResponse> findMyNotifications(NotificationCategory category, Boolean isRead, Pageable pageable);

    /**
     * Unread badge of the caller's inbox, total and per category.
     */
    UnreadCountResponse unreadSummary();

    /**
     * Marks one of the caller's notifications as read ({@code is_read = true}, {@code read_at = now}).
     *
     * @param notificationId id of the notification
     * @return the updated notification
     * @throws com.familyfund.core.exception.ResourceNotFoundException when the notification does not exist or
     *         belongs to another account (never leaked as 403, so ids cannot be probed)
     */
    NotificationResponse markAsRead(String notificationId);

    /**
     * Marks every unread notification of the caller as read, optionally limited to one category.
     *
     * @param category optional category filter; {@code null} means all three categories
     * @return number of notifications that were updated
     */
    int markAllAsRead(NotificationCategory category);

    /**
     * Registers (or re-registers) the caller's FCM registration token.
     *
     * <p>{@code fcm_token} is UNIQUE: when the same device token was previously owned by another account
     * (shared device, re-login) the row is transferred to the caller instead of duplicated.</p>
     *
     * @param fcmToken Firebase Cloud Messaging registration token
     * @param platform {@code Android} | {@code IOS} | {@code Web}; null falls back to {@code Android}
     * @return the {@code notification_device_tokens.token_id}
     */
    String registerDeviceToken(String fcmToken, String platform);

    /**
     * Deactivates a device token (called on logout). Unknown tokens are ignored.
     *
     * @param fcmToken Firebase Cloud Messaging registration token
     */
    void unregisterDeviceToken(String fcmToken);
}
