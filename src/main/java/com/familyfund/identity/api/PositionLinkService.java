package com.familyfund.identity.api;

import com.familyfund.identity.api.dto.ClaimPositionRequest;
import com.familyfund.identity.api.dto.ClaimPositionResponse;
import com.familyfund.identity.api.dto.CreateVirtualMemberRequest;
import com.familyfund.identity.api.dto.CreateVirtualMemberResponse;
import com.familyfund.identity.api.dto.PositionLinkPreviewResponse;

/**
 * Public service contract for the one-time position link code (US-18) and its redemption (US-19).
 */
public interface PositionLinkService {

    /**
     * Creates a virtual (placeholder) member leaf inside a branch of the current clan and issues one
     * {@code PositionLinkCode} bound to it. The profile carries no {@code user_accounts} row yet: it is claimed
     * later by whoever redeems the code (US-19).
     *
     * <p>The profile and the code are written inside one transaction: if any step fails, neither row survives.</p>
     *
     * @param branchId branch that receives the virtual profile, taken from the request path
     *                 ({@code POST /api/v1/branches/{branchId}/virtual-members})
     * @param request  profile payload plus optional expiry of the link
     * @return the created placeholder profile and its one-time position link code
     * @throws com.familyfund.core.exception.BadRequestException          branchId blank / longer than 20 chars,
     *                                                                    expiry in the past (400)
     * @throws com.familyfund.core.exception.ResourceNotFoundException    clan or branch not found in current clan (404)
     * @throws org.springframework.security.access.AccessDeniedException  caller has no account or may not add to this
     *                                                                    branch (403)
     * @throws com.familyfund.core.exception.InvalidGenerationException   generation below branch level (400)
     * @throws com.familyfund.core.exception.DuplicateResourceException   same profile already exists in clan (409)
     */
    CreateVirtualMemberResponse createVirtualMemberWithPositionLink(String branchId, CreateVirtualMemberRequest request);

    /**
     * Previews the position a {@code PositionLinkCode} points at, for the landing page opened from the shared link
     * (US-19). No authentication is required: the code itself is the subject of the query.
     *
     * @param inviteCode the code taken from the shared link
     * @return clan, branch and profile the code belongs to
     * @throws com.familyfund.core.exception.BadRequestException        blank / oversized code, code already used,
     *                                                                  revoked or expired (400)
     * @throws com.familyfund.core.exception.ResourceNotFoundException  unknown code, or a code that is not a
     *                                                                  {@code PositionLinkCode} (404)
     */
    PositionLinkPreviewResponse getPositionLinkPreview(String inviteCode);

    /**
     * Redeems a {@code PositionLinkCode}: the caller's user account is bound to the placeholder profile the code
     * was issued for and becomes {@code Active} immediately - the correct code replaces the manual approval step
     * of the US-17 join flow (US-19).
     *
     * <p>One transaction covers the whole exchange, so a code can never be consumed while the account stays
     * unlinked, and a failed attempt leaves the position, the account and the code untouched. On success the code
     * is spent ({@code used_count = 1}, {@code is_active = false}), the position stops being a placeholder
     * ({@code members.is_placeholder = false}, applied by {@code genealogy::api}) and the account receives
     * {@code ROLE_MEMBER}.</p>
     *
     * @param inviteCode the one-time code handed to the recipient
     * @param request    phone number + password proving who is redeeming, plus an optional email for new accounts
     * @return the linked account and the claimed position
     * @throws com.familyfund.core.exception.BadRequestException          blank/oversized code, expired or already used
     *                                                                    code, wrong password, unusable account (400)
     * @throws com.familyfund.core.exception.ResourceNotFoundException    unknown code, or a code that is not a
     *                                                                    {@code PositionLinkCode} (404)
     * @throws com.familyfund.core.exception.DuplicateResourceException   position already claimed, or the phone
     *                                                                    already linked to another profile (409)
     * @throws org.springframework.security.access.AccessDeniedException  the account is suspended/deactivated (403)
     */
    ClaimPositionResponse claimPosition(String inviteCode, ClaimPositionRequest request);
}