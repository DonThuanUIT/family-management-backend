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
public class UpdateRootBranchRequest {

    @NotBlank(message = "Clan name must not be blank")
    @Size(max = 150, message = "Clan name must not exceed 150 characters")
    private String clanName;

    @NotBlank(message = "Root branch name must not be blank")
    @Size(max = 100, message = "Root branch name must not exceed 100 characters")
    private String branchName;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private String representativeId;
}
