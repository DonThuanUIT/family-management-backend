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
}

