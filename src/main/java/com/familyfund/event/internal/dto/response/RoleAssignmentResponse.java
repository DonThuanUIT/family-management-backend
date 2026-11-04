package com.familyfund.event.internal.dto.response;

import java.time.Instant;

/**
 * Response payload returning details of a role assignment or revocation (US-03).
 */
public record RoleAssignmentResponse(
        String auditId,
        String actionType,
        String roleCode,
        String roleDisplayName,
        String scopeType,
        String scopeId,
        String scopeName,
        String targetMemberId,
        String targetMemberName,
        String targetAccountId,
        String actorAccountId,
        String description,
        Instant timestamp
) {
}
