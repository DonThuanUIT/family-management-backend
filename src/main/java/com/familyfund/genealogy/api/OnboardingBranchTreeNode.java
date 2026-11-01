package com.familyfund.genealogy.api;

import lombok.Builder;

import java.util.List;

/**
 * Public DTO representing a sanitized, lightweight branch tree node for user onboarding.
 * Excludes sensitive personal information of clan members to ensure privacy (US-17).
 */
@Builder
public record OnboardingBranchTreeNode(
        String branchId,
        String clanId,
        String clanName,
        String branchName,
        Integer generationLevel,
        String parentBranchId,
        String representativeName,
        int memberCount,
        List<OnboardingBranchTreeNode> children
) {
    public OnboardingBranchTreeNode {
        if (children == null) {
            children = List.of();
        }
    }
}

