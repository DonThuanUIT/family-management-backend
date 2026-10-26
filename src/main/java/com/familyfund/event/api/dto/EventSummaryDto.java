package com.familyfund.event.api.dto;

import java.time.Instant;

/**
 * Public summary DTO of an event for cross-module consumption.
 */
public record EventSummaryDto(
        String eventId,
        String clanId,
        String eventName,
        EventStatus status,
        Instant eventDatetime
) {
}
