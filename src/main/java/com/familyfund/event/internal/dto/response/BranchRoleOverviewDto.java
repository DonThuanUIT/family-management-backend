package com.familyfund.event.internal.dto.response;

public record BranchRoleOverviewDto(
        String branchId,
        String branchName,
        Integer generationLevel,
        String representativeMemberId,
        String representativeFullName,
        String representativeAccountId
) {
}
