package com.familyfund.identity.api.dto;

import com.familyfund.genealogy.api.dto.VirtualMemberDto;

/**
 * Response payload of {@code POST /api/v1/branches/{branchId}/virtual-members} (US-18).
 *
 * <p>One call persists two rows that belong together: the virtual {@code members} profile and the one-time
 * {@code invitation_codes} row of type {@code PositionLinkCode} that points at it through
 * {@code target_member_id}. They are returned together so the caller can render the placeholder on the tree and
 * hand the redemption link to the person who will claim the position.</p>
 *
 * @param member        the placeholder profile that was created ({@code is_placeholder = true}, no account yet)
 * @param positionLinkCode the one-time code issued for that position ({@code max_uses = 1})
 */
public record CreateVirtualMemberResponse(
        VirtualMemberDto member,
        InviteCodeResponse positionLinkCode
) {}
