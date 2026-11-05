package com.familyfund.core.audit.writer;

import com.familyfund.core.audit.model.AuditAction;
import com.familyfund.core.audit.model.AuditEntityType;
import com.familyfund.core.audit.util.AuditIdGenerator;
import com.familyfund.core.audit.util.AuditSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * Persists sanitized audit log entries to the `audit_logs` table.
 * Executes within the caller's transaction (fail-closed semantics).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final JdbcTemplate jdbcTemplate;
    private final AuditIdGenerator auditIdGenerator;
    private final ObjectMapper objectMapper;

    public void writeLog(
            String clanId,
            String actorAccountId,
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            String targetMemberId,
            Map<String, Object> rawOldValue,
            Map<String, Object> rawNewValue,
            String ipAddress,
            String description
    ) {
        String auditId = auditIdGenerator.generate();
        Map<String, Object> cleanOld = AuditSanitizer.sanitize(rawOldValue);
        Map<String, Object> cleanNew = AuditSanitizer.sanitize(rawNewValue);

        String oldJson = toJson(cleanOld);
        String newJson = toJson(cleanNew);

        String sql = "INSERT INTO audit_logs (" +
                "audit_id, clan_id, actor_account_id, action_type, entity_type, entity_id, " +
                "target_member_id, description, old_value, new_value, ip_address, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)";

        jdbcTemplate.update(
                sql,
                auditId,
                clanId != null ? clanId.trim() : "CLAN_DEFAULT",
                actorAccountId != null ? actorAccountId.trim() : "SYSTEM",
                action.name(),
                entityType.name(),
                entityId != null ? entityId.trim() : null,
                targetMemberId != null ? targetMemberId.trim() : null,
                description,
                oldJson,
                newJson,
                ipAddress,
                Timestamp.from(Instant.now())
        );

        log.info("System audit recorded [auditId={}, action={}, entityType={}, entityId={}, actor={}]",
                auditId, action, entityType, entityId, actorAccountId);
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to serialize audit snapshot to JSON", e);
            return null;
        }
    }
}
