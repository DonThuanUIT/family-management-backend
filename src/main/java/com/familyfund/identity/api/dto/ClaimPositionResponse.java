package com.familyfund.identity.api.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Response payload of {@code POST /api/v1/position-links/{inviteCode}/claim} (US-19).
 *
 * <p>Describes the two rows that were bound together by the redemption: the caller's {@code user_accounts} row
 * (now {@code Active} and linked to {@code member_id}) and the genealogy position that stopped being a placeholder.
 * The account can log in immediately with the phone number and password it redeemed with.</p>
 *
 * @param accountId     {@code user_accounts.account_id} that now owns the position
 * @param phoneNumber   login identifier of that account
 * @param email         optional contact address, echoed back
 * @param accountStatus always {@code Active} after a successful claim (the code replaces the approval step)
 * @param roles         system roles granted by the claim ({@code ROLE_MEMBER})
 * @param memberId      {@code members.member_id} of the claimed position
 * @param fullName      profile name stored on the position (not re-entered by the claimant)
 * @param generation    {@code members.generation} of the claimed position
 * @param branchId      branch the position belongs to ({@code members.branch_id})
 * @param branchName    display name of that branch
 * @param clanId        clan that owns the position
 * @param clanName      display name of that clan
 * @param inviteCode    the one-time code that was consumed
 * @param linkedAt      moment the account and the position were bound
 */
public record ClaimPositionResponse(
        String accountId,
        String phoneNumber,
        String email,
        String accountStatus,
        Set<String> roles,
        String memberId,
        String fullName,
        Integer generation,
        String branchId,
        String branchName,
        String clanId,
        String clanName,
        String inviteCode,
        Instant linkedAt
) {}