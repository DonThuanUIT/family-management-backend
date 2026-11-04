package com.familyfund.event.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.event.internal.dto.request.RoleAssignmentRequest;
import com.familyfund.event.internal.dto.request.RoleRevocationRequest;
import com.familyfund.event.internal.dto.response.AuditLogResponse;
import com.familyfund.event.internal.dto.response.RoleAssignmentResponse;
import com.familyfund.event.internal.dto.response.RoleManagementOverviewResponse;
import com.familyfund.event.internal.service.RoleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Dual-RBAC role assignment, role revocation, and audit logs (US-03).
 * Endpoints:
 * - POST /api/v1/roles/assign
 * - POST /api/v1/roles/revoke
 * - GET  /api/v1/roles/audit-logs
 * - GET  /api/v1/roles/overview
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    /**
     * Chỉ định vai trò cho thành viên (Trưởng nhánh, Trưởng BTC, Thành viên BTC, Thủ quỹ sự kiện).
     * Chỉ Trưởng tộc (ROLE_CLAN_HEAD) hoặc Quản trị hệ thống (ROLE_ADMIN) mới có quyền.
     */
    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<RoleAssignmentResponse>> assignRole(
            @Valid @RequestBody RoleAssignmentRequest request) {
        RoleAssignmentResponse response = roleManagementService.assignRole(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Chỉ định vai trò thành công"));
    }

    /**
     * Thu hồi vai trò đã cấp cho thành viên.
     * Chỉ Trưởng tộc (ROLE_CLAN_HEAD) hoặc Quản trị hệ thống (ROLE_ADMIN) mới có quyền.
     */
    @PostMapping("/revoke")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<RoleAssignmentResponse>> revokeRole(
            @Valid @RequestBody RoleRevocationRequest request) {
        RoleAssignmentResponse response = roleManagementService.revokeRole(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Thu hồi vai trò thành công"));
    }

    /**
     * Tra cứu nhật ký kiểm toán phân quyền (Audit Log).
     */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAuditLogs(
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        List<AuditLogResponse> logs = roleManagementService.getAuditLogs(memberId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.success(logs, "Lấy danh sách nhật ký kiểm toán thành công"));
    }

    /**
     * Lấy dữ liệu tổng hợp phân quyền (danh sách chi nhánh kèm Trưởng nhánh và danh sách thành viên).
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<RoleManagementOverviewResponse>> getOverview() {
        RoleManagementOverviewResponse overview = roleManagementService.getRoleManagementOverview();
        return ResponseEntity.ok(ApiResponse.success(overview, "Lấy thông tin tổng quan phân quyền thành công"));
    }
}
