package com.familyfund.modules.genealogy.controller;

import com.familyfund.core.common.ApiResponse;
import com.familyfund.modules.genealogy.dto.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.UpdateRootBranchRequest;
import com.familyfund.modules.genealogy.service.GenealogyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class GenealogyController {

    private final GenealogyService genealogyService;

    @GetMapping("/root")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> getRootBranch() {
        BranchDetailResponse response = genealogyService.getRootBranch();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/root")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> updateRootBranch(
            @Valid @RequestBody UpdateRootBranchRequest request) {
        BranchDetailResponse response = genealogyService.updateRootBranch(request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin nhánh gốc thành công", response));
    }
}
