package com.familyfund.event.internal.dto.response;

import com.familyfund.event.api.dto.EventRole;

import java.time.Instant;

/**
 * Response DTO representing an assigned personnel record for an event.
 */
public record EventPersonnelResponse(
        String eventId,
        String memberId,
        String memberName,
        EventRole eventRole,
        Boolean isReadOnly,
        Instant assignedDate
) {
}
