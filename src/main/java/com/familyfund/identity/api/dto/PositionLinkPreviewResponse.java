package com.familyfund.identity.api.dto;

import java.time.Instant;

/**
 * Response payload of {@code GET /api/v1/position-links/{inviteCode}} (US-19).
 *
 * <p>A read-only preview for the landing page the recipient opens from the shared link
 * ({@code app.invitation.position-link-base-url}?code=...): it shows which family, which branch and which position
 * the code is about, so the person can confirm it is really theirs before registering. A code that is unknown,
 * already used, revoked or expired is rejected instead of previewed, so this payload always describes a claimable
 * position.</p>
 *
 * @param inviteCode {@code invitation_codes.invite_code} that was looked up
 * @param clanId     clan that owns the position
 * @param clanName   display name of that clan
 * @param branchId   branch of the position ({@code members.branch_id})
 * @param branchName display name of that branch
 * @param memberId   {@code members.member_id} of the placeholder profile
 * @param fullName   name stored on the placeholder profile
 * @param expiresAt  expiry of the code ({@code invitation_codes.expires_at})
 */
public record PositionLinkPreviewResponse(
        String inviteCode,
        String clanId,
        String clanName,
        String branchId,
        String branchName,
        String memberId,
        String fullName,
        Instant expiresAt
) {}