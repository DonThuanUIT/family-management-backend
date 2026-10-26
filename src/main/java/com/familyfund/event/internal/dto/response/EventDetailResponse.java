package com.familyfund.event.internal.dto.response;

import com.familyfund.event.api.dto.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detailed event response including assigned personnel.
 */
public record EventDetailResponse(
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
        List<EventPersonnelResponse> personnel
) {
}
