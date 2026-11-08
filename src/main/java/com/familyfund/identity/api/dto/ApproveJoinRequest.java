package com.familyfund.identity.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional payload for approving a branch join request (US-17).
 *
 * <p>Approving used to accept no body at all. An approver may now correct the applicant's proposed position before
 * the member row is created:</p>
 * <ul>
 *   <li>{@code targetBranchId} - the branch the member is attached to (for {@code ExistingBranchLeaf}) or the
 *       parent branch of the new child branch (for {@code NewChildBranch}). When absent the value proposed by the
 *       applicant is used, so existing clients keep working.</li>
 *   <li>{@code proposedNewBranchName} - optional rename of the child branch, only valid for
 *       {@code NewChildBranch}.</li>
 * </ul>
 */
public record ApproveJoinRequest(
        @Size(max = 20, message = "Mã nhánh không được vượt quá 20 ký tự")
        String targetBranchId,

        @Size(max = 100, message = "Tên chi nhánh mới không được vượt quá 100 ký tự")
        String proposedNewBranchName
) {
}