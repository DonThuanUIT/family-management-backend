package com.familyfund.notification.api.dto;

/**
 * Payload of {@code GET /api/v1/notifications/unread-count}: the unread badge of the caller's inbox (US-39).
 *
 * @param total    unread notifications across every category
 * @param byCategory unread notifications per category; a category with no unread rows is reported as {@code 0}
 */
public record UnreadCountResponse(
        long total,
        CategoryCount byCategory
) {

    public record CategoryCount(long event, long task, long finance) {}
}
