package com.familyfund.modules.genealogy.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.modules.genealogy.dto.request.AssignMemberRequest;
import com.familyfund.modules.genealogy.service.GenealogyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final GenealogyService genealogyService;

    @PutMapping("/{memberId}/assign-branch")
    public ResponseEntity<ApiResponse<Void>> assignMemberToBranch(
            @PathVariable String memberId,
            @Valid @RequestBody AssignMemberRequest request) {
        genealogyService.assignMemberToBranch(memberId, request.branchId());
        return ResponseEntity.ok(ApiResponse.success(null, "Member assigned to family branch successfully"));
    }
}
