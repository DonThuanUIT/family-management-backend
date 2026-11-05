package com.familyfund.core.audit.controller;

import com.familyfund.core.audit.dto.AuditLogPageResponse;
import com.familyfund.core.audit.dto.SystemAuditLogResponse;
import com.familyfund.core.audit.service.AuditQueryService;
import com.familyfund.core.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Read-only REST controller for Clan Head & Admins to search and inspect the System Audit Trail.
 * Endpoint: /api/v1/audit-logs
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<AuditLogPageResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        List<String> actionTypes = (actionType != null && !actionType.isBlank())
                ? Arrays.stream(actionType.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : null;

        List<String> entityTypes = (entityType != null && !entityType.isBlank())
                ? Arrays.stream(entityType.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : null;

        AuditLogPageResponse response = auditQueryService.getAuditLogs(
                page, size, actorId, actionTypes, entityTypes, entityId, from, to
        );

        return ResponseEntity.ok(ApiResponse.success(response, "Tra cứu nhật ký kiểm toán thành công"));
    }

    @GetMapping("/{auditId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<SystemAuditLogResponse>> getAuditLogDetail(
            @PathVariable String auditId
    ) {
        SystemAuditLogResponse response = auditQueryService.getAuditLogDetail(auditId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết bản ghi kiểm toán thành công"));
    }
}
