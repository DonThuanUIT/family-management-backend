package com.familyfund.identity.internal.dto.response;

import java.util.Set;

/**
 * Standard token envelope containing access and refresh tokens along with user context (US-10).
 */
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String accountId,
        String phoneNumber,
        String email,
        String fullName,
        String clanId,
        String memberId,
        Set<String> roles
) {
}
