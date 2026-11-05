package com.familyfund.audit.service;

import com.familyfund.audit.dto.AuditLogPageResponse;
import com.familyfund.audit.dto.SystemAuditLogResponse;

import java.util.List;

public interface AuditQueryService {

    AuditLogPageResponse getAuditLogs(
            int page,
            int size,
            String actorId,
            List<String> actionTypes,
            List<String> entityTypes,
            String entityId,
            String from,
            String to
    );

    SystemAuditLogResponse getAuditLogDetail(String auditId);
}
