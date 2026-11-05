package com.familyfund.core.audit.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Detailed DTO representation of an audit log entry for the Clan Head & System Admins.
 */
public record SystemAuditLogResponse(
        String auditId,
        String clanId,
        String actorAccountId,
        String actorFullName,
        String actionType,
        String entityType,
        String entityId,
        String roleCode,
        String roleDisplayName,
        String scopeType,
        String scopeId,
        String scopeName,
        String targetMemberId,
        String targetMemberName,
        String targetAccountId,
        String description,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String ipAddress,
        Instant createdAt
) {
}
