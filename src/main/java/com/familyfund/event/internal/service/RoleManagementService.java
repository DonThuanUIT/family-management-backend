package com.familyfund.event.internal.service;

import com.familyfund.event.internal.dto.request.RoleAssignmentRequest;
import com.familyfund.event.internal.dto.request.RoleRevocationRequest;
import com.familyfund.event.internal.dto.response.AuditLogResponse;
import com.familyfund.event.internal.dto.response.RoleAssignmentResponse;
import com.familyfund.event.internal.dto.response.RoleManagementOverviewResponse;

import java.util.List;

/**
 * Service interface for Dual-RBAC role assignment, revocation, and audit trail inspection (US-03).
 */
public interface RoleManagementService {

    /**
     * Assigns a role to a member within a branch or event scope.
     *
     * @param request the role assignment payload
     * @return response containing assignment details and generated audit ID
     */
    RoleAssignmentResponse assignRole(RoleAssignmentRequest request);

    /**
     * Revokes a previously assigned role from a member.
     *
     * @param request the role revocation payload
     * @return response containing revocation details and generated audit ID
     */
    RoleAssignmentResponse revokeRole(RoleRevocationRequest request);

    /**
     * Retrieves audit logs for the current clan, optionally filtered by member or scope.
     */
    List<AuditLogResponse> getAuditLogs(String memberId, String scopeType, String scopeId);

    /**
     * Retrieves an overview of clan branches (with current branch heads) and alive members.
     */
    RoleManagementOverviewResponse getRoleManagementOverview();
}
