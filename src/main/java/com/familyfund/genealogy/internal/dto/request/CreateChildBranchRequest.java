package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateChildBranchRequest(
        @NotBlank(message = "Branch name must not be blank")
        @Size(max = 100, message = "Branch name must not exceed 100 characters")
        String branchName,

        @NotBlank(message = "Parent branch ID must not be blank")
        String parentBranchId,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        String representativeId
) {}
