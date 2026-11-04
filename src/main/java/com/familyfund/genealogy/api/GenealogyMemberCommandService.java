package com.familyfund.genealogy.api;

/**
 * Public command contract provided by the Genealogy module for member/branch creation upon onboarding approval (US-17).
 */
public interface GenealogyMemberCommandService {

    /**
     * Creates a new phả hệ member attached to an existing branch.
     *
     * @param command member creation parameters
     * @return the generated memberId
     */
    String createMemberForExistingBranch(CreateOnboardingMemberCommand command);

    /**
     * Creates a new child branch and attaches the first member as its representative.
     *
     * @param command branch and member creation parameters
     * @return the generated memberId
     */
    String createChildBranchAndMember(CreateOnboardingBranchCommand command);

    /**
     * Assigns a new representative member to a family branch.
     *
     * @param branchId                  ID of the branch
     * @param clanId                    ID of the tenant clan
     * @param newRepresentativeMemberId ID of the member to assign as representative
     * @return Optional containing the previous representative memberId if one was assigned and is different
     */
    java.util.Optional<String> assignBranchRepresentative(String branchId, String clanId, String newRepresentativeMemberId);

    /**
     * Revokes representative assignment from a branch.
     *
     * @param branchId       ID of the branch
     * @param clanId         ID of the tenant clan
     * @param targetMemberId ID of the member expected to be the current representative
     */
    void revokeBranchRepresentative(String branchId, String clanId, String targetMemberId);
}

