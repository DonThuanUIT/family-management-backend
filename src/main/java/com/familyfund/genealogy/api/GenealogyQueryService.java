package com.familyfund.genealogy.api;

import java.util.List;
import java.util.Optional;

/**
 * Public query contract provided by the Genealogy module for other bounded contexts.
 */
public interface GenealogyQueryService {

    /**
     * Checks if a family branch exists in the genealogy tree within the current tenant context.
     *
     * @param branchId ID of the branch to check
     * @return true if the branch exists in the current tenant clan, false otherwise
     * @deprecated Prefer {@link #existsBranch(String, String)} to enforce explicit multi-tenant isolation.
     */
    @Deprecated
    boolean existsBranch(String branchId);

    /**
     * Checks if a family branch exists in the genealogy tree for a specific clan.
     *
     * @param clanId   ID of the clan to check within
     * @param branchId ID of the branch to check
     * @return true if the branch exists in the specified clan, false otherwise
     */
    boolean existsBranch(String clanId, String branchId);

    /**
     * Retrieves the display name of a clan.
     *
     * @param clanId ID of the clan
     * @return optional containing clan name if found
     */
    Optional<String> getClanName(String clanId);

    /**
     * Retrieves the display name of a family branch.
     *
     * @param clanId   ID of the clan
     * @param branchId ID of the branch
     * @return optional containing branch name if found
     */
    Optional<String> getBranchName(String clanId, String branchId);

    /**
     * Retrieves the sanitized onboarding branch tree for a clan or branch (US-17).
     *
     * @param clanId         ID of the clan
     * @param targetBranchId optional specific target branch to root the tree at, or null for root branch
     * @return lightweight tree node rooted at clan root or target branch
     */
    OnboardingBranchTreeNode getOnboardingBranchTree(String clanId, String targetBranchId);

    /**
     * Retrieves all branch IDs managed by a specific member as representative (including child branches).
     *
     * @param clanId   ID of the clan
     * @param memberId ID of the representative member
     * @return list of managed branch IDs
     */
    List<String> getBranchIdsManagedByMember(String clanId, String memberId);
}
