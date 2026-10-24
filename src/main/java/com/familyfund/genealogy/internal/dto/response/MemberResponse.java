package com.familyfund.genealogy.internal.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Read model of table `members`. branchId comes from members.branch_id;
 * branchName and clanId are resolved from the related family_branches row.
 */
@Builder
public record MemberResponse(
        String memberId,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        Integer generation,
        String status,
        String deathDateLunar,
        String branchId,
        String branchName,
        String clanId,
        Boolean isPlaceholder,
        Instant createdAt
) {}
