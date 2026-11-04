package com.familyfund.event.api.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when an event task is assigned to an individual member
 * and/or a family branch (US-07).
 */
public record TaskAssignedDomainEvent(
        String eventId,
        String clanId,
        String eventName,
        String taskId,
        String taskName,
        String assignedMemberId,
        String assignedBranchId,
        BigDecimal advanceBudget,
        Instant deadline,
        String triggeredBy
) {
}

