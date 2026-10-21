package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.genealogy.internal.dto.request.CreateChildBranchRequest;
import com.familyfund.genealogy.internal.dto.request.UpdateBranchRequest;
import com.familyfund.genealogy.internal.dto.request.UpdateRootBranchRequest;
import com.familyfund.genealogy.internal.dto.response.BranchDetailResponse;
import com.familyfund.genealogy.internal.dto.response.BranchTreeNodeResponse;
import com.familyfund.genealogy.internal.service.GenealogyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class GenealogyController {

    private final GenealogyService genealogyService;

    @GetMapping("/root")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> getRootBranch() {
        BranchDetailResponse response = genealogyService.getRootBranch();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/root")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> updateRootBranch(
            @Valid @RequestBody UpdateRootBranchRequest request) {
        BranchDetailResponse response = genealogyService.updateRootBranch(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Root branch details updated successfully"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> createChildBranch(
            @Valid @RequestBody CreateChildBranchRequest request) {
        BranchDetailResponse response = genealogyService.createChildBranch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Child branch created successfully"));
    }

    @PutMapping("/{branchId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> updateBranch(
            @PathVariable String branchId,
            @Valid @RequestBody UpdateBranchRequest request) {
        BranchDetailResponse response = genealogyService.updateBranch(branchId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Branch details updated successfully"));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BranchTreeNodeResponse>> getBranchTree() {
        BranchTreeNodeResponse tree = genealogyService.getBranchTree();
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @DeleteMapping("/{branchId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(@PathVariable String branchId) {
        genealogyService.deleteBranch(branchId);
        return ResponseEntity.ok(ApiResponse.success(null, "Branch deleted successfully"));
    }
}
