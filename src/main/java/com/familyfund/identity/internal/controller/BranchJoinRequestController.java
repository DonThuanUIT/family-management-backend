package com.familyfund.identity.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.identity.api.BranchJoinRequestService;
import com.familyfund.identity.api.dto.JoinRequestResponse;
import com.familyfund.identity.api.dto.RejectJoinRequest;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for Managing Branch Join Requests (US-17 Tasks 3 & 5).
 * Allows Clan Head, Branch Head, and Admins to inspect, approve, or reject join proposals.
 */
@RestController
@RequestMapping("/api/v1/join-requests")
@RequiredArgsConstructor
public class BranchJoinRequestController {

    private final BranchJoinRequestService branchJoinRequestService;

    /**
     * Retrieves paged list of join requests for approvers (US-17 Task 5).
     * Clan Head & Admin view requests clan-wide.
     * Branch Head is scoped to their managed branches.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<JoinRequestResponse>>> getJoinRequests(
            @RequestParam(required = false) JoinRequestApprovalStatus status,
            @RequestParam(required = false) String branchId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<JoinRequestResponse> page = branchJoinRequestService.getJoinRequests(status, branchId, pageable, authentication);
        return ResponseEntity.ok(ApiResponse.success(page, "Lấy danh sách yêu cầu gia nhập thành công"));
    }

    /**
     * Approves a branch join request (US-17 Task 3).
     * Creates Member (and Child Branch if proposed), activates user account, and assigns ROLE_MEMBER.
     */
    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> approveJoinRequest(
            @PathVariable String requestId,
            Authentication authentication
    ) {
        JoinRequestResponse response = branchJoinRequestService.approveJoinRequest(requestId, authentication);
        return ResponseEntity.ok(ApiResponse.success(response, "Phê duyệt yêu cầu gia nhập thành công"));
    }

    /**
     * Rejects a branch join request with a specific reason (US-17 Task 3).
     * Deactivates temporary user account and marks request as Rejected.
     */
    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> rejectJoinRequest(
            @PathVariable String requestId,
            @Valid @RequestBody RejectJoinRequest request,
            Authentication authentication
    ) {
        JoinRequestResponse response = branchJoinRequestService.rejectJoinRequest(requestId, request, authentication);
        return ResponseEntity.ok(ApiResponse.success(response, "Từ chối yêu cầu gia nhập thành công"));
    }
}

