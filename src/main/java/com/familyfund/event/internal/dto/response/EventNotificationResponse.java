package com.familyfund.event.internal.dto.response;

import java.time.Instant;

/**
 * Response DTO representing an event notification / announcement.
 */
public record EventNotificationResponse(
        String notificationId,
        String eventId,
        String title,
        String contentChanges,
        Instant sentTime,
        String triggeredBy
) {
}
