package com.familyfund.modules.genealogy.dto;

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
public class UpdateBranchRequest {

    @NotBlank(message = "Tên chi/nhánh không được để trống")
    @Size(max = 100, message = "Tên chi/nhánh không được vượt quá 100 ký tự")
    private String branchName;

    private String parentBranchId;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    private String representativeId;
}
