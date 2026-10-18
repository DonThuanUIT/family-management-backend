package com.familyfund.modules.genealogy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateBranchRequest(
        @NotBlank(message = "Branch name must not be blank")
        @Size(max = 100, message = "Branch name must not exceed 100 characters")
        String branchName,

        String parentBranchId,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        String representativeId
) {}
