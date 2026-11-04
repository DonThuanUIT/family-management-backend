package com.familyfund.event.internal.dto.request;

import com.familyfund.event.api.dto.AssignableRoleCode;
import com.familyfund.event.api.dto.RoleScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for assigning a role to a member (US-03).
 */
public record RoleAssignmentRequest(
        @NotBlank(message = "Mã thành viên (memberId) không được để trống")
        @Size(max = 20, message = "Mã thành viên không vượt quá 20 ký tự")
        String memberId,

        @NotNull(message = "Vai trò (roleCode) không được để trống")
        AssignableRoleCode roleCode,

        @NotNull(message = "Phạm vi phân quyền (scopeType: BRANCH hoặc EVENT) không được để trống")
        RoleScopeType scopeType,

        @NotBlank(message = "Mã phạm vi (scopeId: branchId hoặc eventId) không được để trống")
        @Size(max = 20, message = "Mã phạm vi không vượt quá 20 ký tự")
        String scopeId,

        String description
) {
}
