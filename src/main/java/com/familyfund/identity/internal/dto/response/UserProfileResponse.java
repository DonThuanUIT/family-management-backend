package com.familyfund.identity.internal.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.util.Set;

/**
 * Combined profile view DTO for the currently authenticated member (US-22).
 * Exposes account contacts and read-only genealogy demographics.
 */
@Builder
public record UserProfileResponse(
        String accountId,
        String memberId,
        String clanId,
        String accountStatus,
        Set<String> roles,

        // Contact info (modifiable by user)
        String phoneNumber,
        String email,
        String address,

        // Genealogy demographics (read-only for normal member)
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        Integer generation,
        String branchId,
        String branchName
) {
}
