package com.familyfund.genealogy.internal.security;

import lombok.Builder;

import java.util.Collections;
import java.util.Set;

/**
 * Encapsulates the viewer's security identity and management scope
 * used to evaluate privacy rules against member profiles (US-25).
 */
@Builder
public record PrivacyEvaluationContext(
        String callerMemberId,
        boolean isClanHead,
        Set<String> managedBranchIds,
        boolean isAuthorizedOrganizer
) {
    public PrivacyEvaluationContext {
        if (managedBranchIds == null) {
            managedBranchIds = Collections.emptySet();
        }
    }

    public boolean isSelf(String targetMemberId) {
        return callerMemberId != null
                && targetMemberId != null
                && callerMemberId.equalsIgnoreCase(targetMemberId.trim());
    }

    public boolean canManageBranch(String branchId) {
        return isClanHead || (branchId != null && managedBranchIds.contains(branchId));
    }
}
