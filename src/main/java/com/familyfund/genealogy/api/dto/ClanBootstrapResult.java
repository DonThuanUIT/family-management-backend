package com.familyfund.genealogy.api.dto;

import java.time.LocalDate;

/**
 * Result data returned after bootstrapping a new clan and its root family tree structure.
 */
public record ClanBootstrapResult(
        String clanId,
        String clanName,
        String ancestorName,
        String originLocation,
        Integer establishedYear,
        String description,
        String rootBranchId,
        String rootBranchName,
        String creatorMemberId,
        String creatorFullName,
        String creatorGender,
        Integer creatorGeneration,
        LocalDate creatorDateOfBirth
) {
}
