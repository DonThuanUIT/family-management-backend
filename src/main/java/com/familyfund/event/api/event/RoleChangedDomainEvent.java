package com.familyfund.event.api.event;

import com.familyfund.event.api.dto.AssignableRoleCode;
import com.familyfund.event.api.dto.RoleActionType;
import com.familyfund.event.api.dto.RoleScopeType;

import java.time.Instant;

/**
 * Domain event dispatched when a role is assigned or revoked (US-03).
 */
public record RoleChangedDomainEvent(
        String auditId,
        String clanId,
        String actorAccountId,
        String targetMemberId,
        String targetAccountId,
        RoleActionType actionType,
        AssignableRoleCode roleCode,
        RoleScopeType scopeType,
        String scopeId,
        String scopeName,
        String description,
        Instant timestamp
) {
}
