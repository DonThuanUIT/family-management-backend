package com.familyfund.event.internal.dto.response;

import java.time.Instant;

/**
 * Response representing an entry in the role management audit log (US-03).
 */
public record AuditLogResponse(
        String auditId,
        String clanId,
        String actorAccountId,
        String actorFullName,
        String targetMemberId,
        String targetFullName,
        String targetAccountId,
        String actionType,
        String roleCode,
        String roleDisplayName,
        String scopeType,
        String scopeId,
        String scopeName,
        String description,
        Instant createdAt
) {
}
