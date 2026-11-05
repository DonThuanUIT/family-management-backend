package com.familyfund.audit.snapshot;

import com.familyfund.audit.model.AuditEntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventAuditSnapshotProvider implements AuditSnapshotProvider {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public AuditEntityType supports() {
        return AuditEntityType.EVENT;
    }

    @Override
    public Optional<Map<String, Object>> snapshot(String entityId, String clanId) {
        if (entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        try {
            String sql = "SELECT event_id, clan_id, event_name, event_datetime, event_date_lunar, " +
                    "location, estimated_budget, description, status, created_by, closed_at " +
                    "FROM events WHERE event_id = ?";
            return jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("eventId", rs.getString("event_id"));
                    map.put("clanId", rs.getString("clan_id"));
                    map.put("eventName", rs.getString("event_name"));
                    map.put("eventDatetime", rs.getTimestamp("event_datetime") != null ? rs.getTimestamp("event_datetime").toInstant().toString() : null);
                    map.put("eventDateLunar", rs.getString("event_date_lunar"));
                    map.put("location", rs.getString("location"));
                    map.put("estimatedBudget", rs.getBigDecimal("estimated_budget") != null ? rs.getBigDecimal("estimated_budget").toPlainString() : "0");
                    map.put("description", rs.getString("description"));
                    map.put("status", rs.getString("status"));
                    map.put("createdBy", rs.getString("created_by"));
                    map.put("closedAt", rs.getTimestamp("closed_at") != null ? rs.getTimestamp("closed_at").toInstant().toString() : null);
                    return Optional.of(map);
                }
                return Optional.empty();
            }, entityId.trim());
        } catch (Exception ex) {
            log.warn("Failed to capture event snapshot for eventId={}: {}", entityId, ex.getMessage());
            return Optional.empty();
        }
    }
}
