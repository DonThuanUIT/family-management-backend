package com.familyfund.genealogy.api.dto;

import java.time.LocalDate;

/**
 * Detailed profile demographic of a genealogy member exposed across bounded contexts (US-22).
 */
public record MemberProfileDetailsDto(
        String memberId,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        Integer generation,
        String clanId,
        String branchId,
        String branchName
) {
}
