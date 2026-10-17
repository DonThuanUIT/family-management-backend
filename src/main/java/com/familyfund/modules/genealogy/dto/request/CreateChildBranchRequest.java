package com.familyfund.modules.genealogy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChildBranchRequest {

    @NotBlank(message = "Branch name must not be blank")
    @Size(max = 100, message = "Branch name must not exceed 100 characters")
    private String branchName;

    @NotBlank(message = "Parent branch ID must not be blank")
    private String parentBranchId;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private String representativeId;
}
