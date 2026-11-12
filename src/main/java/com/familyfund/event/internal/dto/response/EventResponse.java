package com.familyfund.event.internal.dto.response;

import com.familyfund.event.api.dto.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Standard response DTO for an event.
 */
public record EventResponse(
        String eventId,
        String clanId,
        String eventName,
        Instant eventDatetime,
        String eventDateLunar,
        String location,
        BigDecimal estimatedBudget,
        String description,
        EventStatus status,
        String createdBy,
        Instant closedAt,
        Instant registrationDeadline,
        Integer maxCapacity,
        Integer currentAttendees,
        Integer remainingSlots
) {
    public EventResponse(
            String eventId,
            String clanId,
            String eventName,
            Instant eventDatetime,
            String eventDateLunar,
            String location,
            BigDecimal estimatedBudget,
            String description,
            EventStatus status,
            String createdBy,
            Instant closedAt
    ) {
        this(
                eventId, clanId, eventName, eventDatetime, eventDateLunar,
                location, estimatedBudget, description, status, createdBy, closedAt,
                null, null, 0, null
        );
    }
}
