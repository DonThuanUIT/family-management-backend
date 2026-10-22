package com.familyfund.genealogy.api.dto;

import java.time.LocalDate;

/**
 * Command payload to bootstrap a new clan, root branch, optional ancestor, and initial creator member.
 */
public record ClanBootstrapCommand(
        String clanName,
        String ancestorName,
        String originLocation,
        Integer establishedYear,
        String description,
        String creatorFullName,
        String creatorGender,
        Integer creatorGeneration,
        LocalDate creatorDateOfBirth
) {
}
