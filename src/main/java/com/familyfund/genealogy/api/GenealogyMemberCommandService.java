package com.familyfund.genealogy.api;

import com.familyfund.genealogy.api.dto.VirtualMemberDto;

/**
 * Public command contract provided by the Genealogy module for member/branch creation upon onboarding approval (US-17)
 * and for virtual placeholder profiles created by a clan head / branch head (US-18).
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
     * Creates a virtual (placeholder) leaf profile inside an existing branch of the caller's clan (US-18).
     *
     * <p>Every domain invariant of member creation is reused from the genealogy member service: the branch must
     * exist in the tenant clan (404), the caller must be allowed to add to that branch (403), the generation may
     * not be below the branch generation level (400) and the clan-wide duplicate profile guard applies (409).
     * The profile is always persisted with {@code is_placeholder = true} and never linked to a user account.</p>
     *
     * @param command the profile to create
     * @return the persisted profile, including the server-generated {@code member_id}
     */
    VirtualMemberDto createVirtualMember(CreateVirtualMemberCommand command);

    /**
     * Claims a virtual (placeholder) position for a real person (US-19), turning
     * {@code members.is_placeholder} from {@code true} to {@code false}.
     *
     * <p>The position is located by {@code clanId + memberId}, so this call does not depend on the
     * {@code TenantContext} being set to the owning clan: a redemption request is anonymous and the clan comes from
     * the {@code PositionLinkCode} that was redeemed. Every other attribute of the profile is left untouched, which
     * is what preserves the events, contributions and advances already recorded on that {@code member_id}.</p>
     *
     * @param command the tenant that owns the position and the placeholder {@code member_id}
     * @return the claimed profile, with {@code isPlaceholder = false}
     * @throws com.familyfund.core.exception.BadRequestException         command or its ids are blank
     * @throws com.familyfund.core.exception.ResourceNotFoundException    no such position in that clan (404)
     * @throws com.familyfund.core.exception.DuplicateResourceException  the position was already claimed (409)
     */
    VirtualMemberDto claimVirtualMember(ClaimVirtualMemberCommand command);

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

