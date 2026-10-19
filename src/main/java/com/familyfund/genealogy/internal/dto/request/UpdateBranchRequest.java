package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateBranchRequest(
        @Size(max = 100, message = "Branch name must not exceed 100 characters")
        String branchName,

        String parentBranchId,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        String representativeId
) {}
