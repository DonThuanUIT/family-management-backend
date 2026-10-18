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

    private String resolveClanId(String headerClanId, String queryClanId) {
        if (queryClanId != null && !queryClanId.isBlank()) {
            return queryClanId.trim();
        }
        if (headerClanId != null && !headerClanId.isBlank()) {
            return headerClanId.trim();
        }
        return GenealogyService.DEFAULT_CLAN_ID;
    }

    @GetMapping("/root")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> getRootBranch(
            @RequestHeader(value = "X-Clan-Id", required = false) String headerClanId,
            @RequestParam(value = "clanId", required = false) String queryClanId) {
        String clanId = resolveClanId(headerClanId, queryClanId);
        BranchDetailResponse response = genealogyService.getRootBranch(clanId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/root")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> updateRootBranch(
            @RequestHeader(value = "X-Clan-Id", required = false) String headerClanId,
            @RequestParam(value = "clanId", required = false) String queryClanId,
            @Valid @RequestBody UpdateRootBranchRequest request) {
        String clanId = resolveClanId(headerClanId, queryClanId);
        BranchDetailResponse response = genealogyService.updateRootBranch(clanId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Root branch details updated successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BranchDetailResponse>> createChildBranch(
            @RequestHeader(value = "X-Clan-Id", required = false) String headerClanId,
            @RequestParam(value = "clanId", required = false) String queryClanId,
            @Valid @RequestBody CreateChildBranchRequest request) {
        String clanId = resolveClanId(headerClanId, queryClanId);
        BranchDetailResponse response = genealogyService.createChildBranch(clanId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Child branch created successfully"));
    }

    @PutMapping("/{branchId}")
    public ResponseEntity<ApiResponse<BranchDetailResponse>> updateBranch(
            @RequestHeader(value = "X-Clan-Id", required = false) String headerClanId,
            @RequestParam(value = "clanId", required = false) String queryClanId,
            @PathVariable String branchId,
            @Valid @RequestBody UpdateBranchRequest request) {
        String clanId = resolveClanId(headerClanId, queryClanId);
        BranchDetailResponse response = genealogyService.updateBranch(clanId, branchId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Branch details updated successfully"));
    }

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<BranchTreeNodeResponse>> getBranchTree(
            @RequestHeader(value = "X-Clan-Id", required = false) String headerClanId,
            @RequestParam(value = "clanId", required = false) String queryClanId) {
        String clanId = resolveClanId(headerClanId, queryClanId);
        BranchTreeNodeResponse tree = genealogyService.getBranchTree(clanId);
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @DeleteMapping("/{branchId}")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(
            @RequestHeader(value = "X-Clan-Id", required = false) String headerClanId,
            @RequestParam(value = "clanId", required = false) String queryClanId,
            @PathVariable String branchId) {
        String clanId = resolveClanId(headerClanId, queryClanId);
        genealogyService.deleteBranch(clanId, branchId);
        return ResponseEntity.ok(ApiResponse.success(null, "Branch deleted successfully"));
    }

    @PutMapping("/{branchId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> assignMember(
            @RequestHeader(value = "X-Clan-Id", required = false) String headerClanId,
            @RequestParam(value = "clanId", required = false) String queryClanId,
            @PathVariable String branchId,
            @PathVariable String memberId) {
        String clanId = resolveClanId(headerClanId, queryClanId);
        genealogyService.assignMemberToBranch(clanId, memberId, branchId);
        return ResponseEntity.ok(ApiResponse.success(null, "Member assigned to branch successfully"));
    }
}
