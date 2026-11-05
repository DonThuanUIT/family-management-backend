package com.familyfund.core.audit.snapshot;

import com.familyfund.core.audit.model.AuditEntityType;
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
public class BranchAuditSnapshotProvider implements AuditSnapshotProvider {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public AuditEntityType supports() {
        return AuditEntityType.BRANCH;
    }

    @Override
    public Optional<Map<String, Object>> snapshot(String entityId, String clanId) {
        if (entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        try {
            String sql;
            Object[] queryArgs;
            if ("ROOT".equalsIgnoreCase(entityId.trim())) {
                sql = "SELECT branch_id, clan_id, branch_name, generation_level, parent_branch_id, representative_id, description " +
                        "FROM family_branches WHERE clan_id = ? AND parent_branch_id IS NULL";
                queryArgs = new Object[]{clanId};
            } else {
                sql = "SELECT branch_id, clan_id, branch_name, generation_level, parent_branch_id, representative_id, description " +
                        "FROM family_branches WHERE branch_id = ?";
                queryArgs = new Object[]{entityId.trim()};
            }

            return jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("branchId", rs.getString("branch_id"));
                    map.put("clanId", rs.getString("clan_id"));
                    map.put("branchName", rs.getString("branch_name"));
                    map.put("generationLevel", rs.getInt("generation_level"));
                    map.put("parentBranchId", rs.getString("parent_branch_id"));
                    map.put("representativeId", rs.getString("representative_id"));
                    map.put("description", rs.getString("description"));
                    return Optional.of(map);
                }
                return Optional.empty();
            }, queryArgs);
        } catch (Exception ex) {
            log.warn("Failed to capture branch snapshot for branchId={}: {}", entityId, ex.getMessage());
            return Optional.empty();
        }
    }
}
