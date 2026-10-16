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
public class UpdateRootBranchRequest {

    @NotBlank(message = "Tên dòng họ không được để trống")
    @Size(max = 150, message = "Tên dòng họ không được vượt quá 150 ký tự")
    private String clanName;

    @NotBlank(message = "Tên nhánh gốc không được để trống")
    @Size(max = 100, message = "Tên nhánh gốc không được vượt quá 100 ký tự")
    private String branchName;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    private String representativeId;
}
