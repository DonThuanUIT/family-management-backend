package com.familyfund.modules.genealogy.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.modules.genealogy.dto.request.CreateChildBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateRootBranchRequest;
import com.familyfund.modules.genealogy.dto.response.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.response.BranchTreeNodeResponse;
import com.familyfund.modules.genealogy.service.GenealogyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        return ResponseEntity.ok(ApiResponse.success(response, "Root branch details updated successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BranchDetailResponse>> createChildBranch(
            @Valid @RequestBody CreateChildBranchRequest request) {
        BranchDetailResponse response = genealogyService.createChildBranch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Child branch created successfully"));
    }

    @PutMapping("/{branchId}")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> updateBranch(
            @PathVariable String branchId,
            @Valid @RequestBody UpdateBranchRequest request) {
        BranchDetailResponse response = genealogyService.updateBranch(branchId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Branch details updated successfully"));
    }

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<BranchTreeNodeResponse>> getBranchTree() {
        BranchTreeNodeResponse tree = genealogyService.getBranchTree();
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @DeleteMapping("/{branchId}")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(@PathVariable String branchId) {
        genealogyService.deleteBranch(branchId);
        return ResponseEntity.ok(ApiResponse.success(null, "Branch deleted successfully"));
    }

    @PutMapping("/{branchId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> assignMember(
            @PathVariable String branchId,
            @PathVariable String memberId) {
        genealogyService.assignMemberToBranch(memberId, branchId);
        return ResponseEntity.ok(ApiResponse.success(null, "Member assigned to branch successfully"));
    }
}
