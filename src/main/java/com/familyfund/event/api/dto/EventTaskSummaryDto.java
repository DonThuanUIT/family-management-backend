package com.familyfund.event.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public DTO exposing task metadata for cross-module financial and advance voucher operations (US-46).
 */
public record EventTaskSummaryDto(
        String taskId,
        String eventId,
        String clanId,
        String taskName,
        BigDecimal advanceBudget,
        Instant deadline,
        TaskStatus status,
        String assignedMemberId,
        String assignedBranchId
) {}
