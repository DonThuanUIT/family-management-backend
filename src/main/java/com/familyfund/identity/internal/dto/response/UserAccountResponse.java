package com.familyfund.identity.internal.dto.response;

import java.time.Instant;
import java.util.Set;

public record UserAccountResponse(
        String accountId,
        String phoneNumber,
        String email,
        String accountStatus,
        Set<String> roles,
        Instant createdAt
) {
}
