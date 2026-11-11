package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.internal.dto.request.CreateMemberRequest;
import com.familyfund.genealogy.internal.dto.request.MemberSearchCriteria;
import com.familyfund.genealogy.internal.dto.request.UpdateLifeStatusRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;

import java.util.List;

/**
 * Application service for genealogy member profiles (table `members`).
 */
public interface MemberService {

    MemberResponse getMember(String memberId);

    /**
     * Creates a placeholder member profile (is_placeholder = true) in a branch of the current clan.
     *
     * @param branchId target branch, taken from the request path (POST /api/v1/branches/{branchId}/members)
     * @param request  profile payload; the branch is never read from the body
     * @throws com.familyfund.core.exception.BadRequestException          branchId blank or longer than 20 chars (400)
     * @throws com.familyfund.core.exception.ResourceNotFoundException    clan or branch not found in current clan (404)
     * @throws org.springframework.security.access.AccessDeniedException  caller may not add to this branch (403)
     * @throws com.familyfund.core.exception.InvalidGenerationException   generation below branch level (400)
     * @throws com.familyfund.core.exception.DuplicateResourceException   same profile already exists in clan (409)
     */
    MemberResponse createMember(String branchId, CreateMemberRequest request);

    /**
     * Lists all member profiles residing in the given branch of the caller's clan.
     *
     * @param branchId target branch ID
     * @return list of member profiles
     */
    List<MemberResponse> getMembersByBranch(String branchId);

    /**
     * Lists all member profiles in the caller's clan.
     *
     * @return list of all member profiles in the clan
     */
    List<MemberResponse> getMembersByClan();

    /**
     * Searches member profiles within the caller's clan based on flexible search criteria (US-30 / Task #111).
     * Supports accent-insensitive (unaccented) fuzzy matching on full name, partial matching on member ID,
     * generation level, and branch ID.
     *
     * @param criteria search filter criteria
     * @return matching member profiles
     */
    List<MemberResponse> searchMembers(MemberSearchCriteria criteria);

    /**
     * Updates life status (Living/Deceased) of a member, calculates lunar anniversary,
     * and publishes domain event to lock account if deceased (US-27).
     *
     * @param memberId target member ID
     * @param request  status update payload
     * @return updated member profile
     */
    MemberResponse updateLifeStatus(String memberId, UpdateLifeStatusRequest request);
}
