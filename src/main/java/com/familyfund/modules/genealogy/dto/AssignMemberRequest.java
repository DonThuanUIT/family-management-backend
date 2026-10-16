package com.familyfund.modules.genealogy.dto;

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

    @NotBlank(message = "Mã chi nhánh không được để trống")
    private String branchId;
}
