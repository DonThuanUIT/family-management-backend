package com.familyfund.event.internal.dto.response;

import com.familyfund.event.api.dto.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO representing an event task with enriched assignee names (US-07).
 */
public record TaskResponse(
        String taskId,
        String eventId,
        String taskName,
        String details,
        BigDecimal advanceBudget,
        Instant deadline,
        TaskStatus status,
        String assignedBranchId,
        String assignedBranchName,
        String assignedMemberId,
        String assignedMemberName,
        String createdBy
) {
}

