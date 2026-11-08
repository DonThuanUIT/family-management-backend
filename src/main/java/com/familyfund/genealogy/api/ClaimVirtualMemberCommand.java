package com.familyfund.genealogy.api;

import lombok.Builder;

/**
 * Command payload for claiming a virtual (placeholder) position with the one-time position link code issued by
 * the identity module (US-18), so the profile becomes a real member owned by a user account (US-19).
 *
 * <p>Claiming is the only way a placeholder becomes a regular profile: the profile keeps every attribute it was
 * created with (name, generation, branch, events, contributions) and only loses
 * {@code members.is_placeholder = true}, so history recorded on that {@code member_id} is preserved. The tenant is
 * passed explicitly (instead of being read from the {@code TenantContext}) because the redemption request is
 * anonymous and the owning clan is the one recorded on the invitation code, not the one on the request.</p>
 *
 * <p>Nothing here accepts a user account, a branch or a new profile: the branch is already fixed by
 * {@code members.branch_id} and the account binding lives in the identity module.</p>
 *
 * @param clanId   tenant that owns the position ({@code invitation_codes.clan_id})
 * @param memberId {@code members.member_id} of the placeholder profile to claim
 */
@Builder
public record ClaimVirtualMemberCommand(
        String clanId,
        String memberId
) {}