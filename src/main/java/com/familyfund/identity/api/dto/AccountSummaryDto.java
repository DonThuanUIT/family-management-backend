package com.familyfund.identity.api.dto;

import java.util.Set;

/**
 * Summary DTO of a user account exposed across bounded contexts.
 */
public record AccountSummaryDto(
        String accountId,
        String memberId,
        String phoneNumber,
        String email,
        String accountStatus,
        Set<String> roles
) {
}
