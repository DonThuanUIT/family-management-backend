package com.familyfund.modules.genealogy.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignMemberRequest {

    @NotBlank(message = "Branch ID must not be blank")
    private String branchId;
}
