package com.familyfund.event.internal.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Request payload for assigning or reassigning a task to a member or family branch (US-07).
 */
public record AssignTaskRequest(
        @Size(max = 20, message = "Mã chi nhánh tối đa 20 ký tự")
        String assignedBranchId,

        @Size(max = 20, message = "Mã thành viên tối đa 20 ký tự")
        String assignedMemberId
) {
    @AssertTrue(message = "Phải chỉ định ít nhất cá nhân hoặc nhánh gia đình phụ trách")
    public boolean hasAtLeastOneAssignee() {
        return (assignedBranchId != null && !assignedBranchId.isBlank()) ||
               (assignedMemberId != null && !assignedMemberId.isBlank());
    }
}

