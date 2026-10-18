package com.familyfund.modules.genealogy.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record BranchDetailResponse(
        String branchId,
        String clanId,
        String clanName,
        String branchName,
        Integer generationLevel,
        String parentBranchId,
        String representativeId,
        String representativeName,
        String description,
        Instant createdAt
) {}
