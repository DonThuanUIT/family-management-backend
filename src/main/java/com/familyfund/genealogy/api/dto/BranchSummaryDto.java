package com.familyfund.genealogy.api.dto;

/**
 * Summary DTO of a family branch exposed across bounded contexts.
 */
public record BranchSummaryDto(
        String branchId,
        String clanId,
        String branchName,
        Integer generationLevel,
        String representativeMemberId,
        String representativeFullName
) {
}
