package com.familyfund.genealogy.api;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Command payload for creating a new child branch and representative member upon onboarding approval (US-17).
 */
@Builder
public record CreateOnboardingBranchCommand(
        String clanId,
        String parentBranchId,
        String newBranchName,
        String memberFullName,
        LocalDate memberDateOfBirth,
        String memberGender,
        Integer memberGeneration
) {}

