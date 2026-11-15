package com.familyfund.notification.api.dto;

import com.familyfund.notification.api.NotificationCategory;
import lombok.Builder;

import java.time.Instant;

/**
 * Read model of table {@code notifications} returned by the inbox endpoints (US-39).
 */
@Builder
public record NotificationResponse(
        String notificationId,
        String clanId,
        NotificationCategory category,
        String title,
        String content,
        String referenceType,
        String referenceId,
        Boolean isRead,
        Instant readAt,
        Instant createdAt
) {}
