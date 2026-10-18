package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record AssignMemberRequest(
        @NotBlank(message = "Branch ID must not be blank")
        String branchId
) {}
