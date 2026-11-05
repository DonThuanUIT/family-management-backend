package com.familyfund.audit.snapshot;

import com.familyfund.audit.model.AuditEntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPersonnelAuditSnapshotProvider implements AuditSnapshotProvider {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public AuditEntityType supports() {
        return AuditEntityType.EVENT_PERSONNEL;
    }

    @Override
    public Optional<Map<String, Object>> snapshot(String entityId, String clanId) {
        if (entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        try {
            String sql = "SELECT ep.event_id, ep.member_id, m.full_name, ep.event_role, ep.is_read_only, ep.assigned_date " +
                    "FROM event_personnel ep " +
                    "LEFT JOIN members m ON ep.member_id = m.member_id " +
                    "WHERE ep.event_id = ? ORDER BY ep.member_id, ep.event_role";

            List<Map<String, Object>> personnelList = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("memberId", rs.getString("member_id"));
                item.put("fullName", rs.getString("full_name"));
                item.put("eventRole", rs.getString("event_role"));
                item.put("isReadOnly", rs.getBoolean("is_read_only"));
                item.put("assignedDate", rs.getTimestamp("assigned_date") != null ? rs.getTimestamp("assigned_date").toInstant().toString() : null);
                return item;
            }, entityId.trim());

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("eventId", entityId.trim());
            root.put("personnel", personnelList);
            return Optional.of(root);
        } catch (Exception ex) {
            log.warn("Failed to capture event personnel snapshot for eventId={}: {}", entityId, ex.getMessage());
            return Optional.empty();
        }
    }
}
