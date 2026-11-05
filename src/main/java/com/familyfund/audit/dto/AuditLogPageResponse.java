package com.familyfund.audit.dto;

import java.util.List;

/**
 * Paginated response wrapper for audit logs.
 */
public record AuditLogPageResponse(
        List<SystemAuditLogResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
