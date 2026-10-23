package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.internal.dto.request.CreateMemberRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;

/**
 * Application service for genealogy member profiles (table `members`).
 */
public interface MemberService {

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
}
