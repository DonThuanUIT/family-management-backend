package com.familyfund.genealogy.api;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Command payload for creating a phả hệ member in an existing branch upon onboarding approval (US-17).
 */
@Builder
public record CreateOnboardingMemberCommand(
        String clanId,
        String branchId,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        Integer generation
) {}

