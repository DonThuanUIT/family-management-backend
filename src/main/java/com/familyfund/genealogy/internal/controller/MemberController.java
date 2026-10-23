package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.genealogy.internal.dto.request.AssignMemberRequest;
import com.familyfund.genealogy.internal.dto.request.CreateMemberRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.service.GenealogyService;
import com.familyfund.genealogy.internal.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Member endpoints of the genealogy bounded context.
 *
 * <p>Creation is nested under the owning branch ({@code POST /api/v1/branches/{branchId}/members})
 * so the resource hierarchy of the API mirrors {@code members.branch_id -> family_branches.branch_id}.
 * The class deliberately has no class-level {@code @RequestMapping}: the two endpoints live under
 * different resource roots ({@code /branches} and {@code /members}).</p>
 *
 * <p>The contract of both endpoints is documented in {@code docker/swagger/openapi.yaml}, which is
 * served by the Swagger UI container on port 8082.</p>
 */
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final GenealogyService genealogyService;
    private final MemberService memberService;

    /**
     * Creates a placeholder member profile inside the given branch of the current clan.
     *
     * @param branchId branch that receives the member, taken from the path
     * @return {@code 201 Created} with the persisted member
     */
    @PostMapping("/api/v1/branches/{branchId}/members")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<MemberResponse>> createMember(
            @PathVariable String branchId,
            @Valid @RequestBody CreateMemberRequest request) {
        MemberResponse response = memberService.createMember(branchId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Member created successfully"));
    }

    @PutMapping("/api/v1/members/{memberId}/assign-branch")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignMemberToBranch(
            @PathVariable String memberId,
            @Valid @RequestBody AssignMemberRequest request) {
        genealogyService.assignMemberToBranch(memberId, request.branchId());
        return ResponseEntity.ok(ApiResponse.success(null, "Member assigned to family branch successfully"));
    }
}
