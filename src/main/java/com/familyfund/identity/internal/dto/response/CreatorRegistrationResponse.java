package com.familyfund.identity.internal.dto.response;

public record CreatorRegistrationResponse(
        UserAccountResponse account,
        ClanResponse clan,
        MemberResponse member
) {
}
