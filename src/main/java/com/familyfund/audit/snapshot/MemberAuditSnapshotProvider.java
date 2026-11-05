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
public class MemberAuditSnapshotProvider implements AuditSnapshotProvider {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public AuditEntityType supports() {
        return AuditEntityType.MEMBER;
    }

    @Override
    public Optional<Map<String, Object>> snapshot(String entityId, String clanId) {
        if (entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        try {
            String sql = "SELECT m.member_id, m.full_name, m.date_of_birth, m.gender, m.generation, m.status, " +
                    "m.death_date_lunar, m.branch_id, b.branch_name, b.clan_id " +
                    "FROM members m LEFT JOIN family_branches b ON m.branch_id = b.branch_id " +
                    "WHERE m.member_id = ?";
            return jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("memberId", rs.getString("member_id"));
                    map.put("fullName", rs.getString("full_name"));
                    map.put("dateOfBirth", rs.getDate("date_of_birth") != null ? rs.getDate("date_of_birth").toString() : null);
                    map.put("gender", rs.getString("gender"));
                    map.put("generation", rs.getInt("generation"));
                    map.put("status", rs.getString("status"));
                    map.put("deathDateLunar", rs.getString("death_date_lunar"));
                    map.put("branchId", rs.getString("branch_id"));
                    map.put("branchName", rs.getString("branch_name"));
                    map.put("clanId", rs.getString("clan_id"));
                    return Optional.of(map);
                }
                return Optional.empty();
            }, entityId.trim());
        } catch (Exception ex) {
            log.warn("Failed to capture member snapshot for memberId={}: {}", entityId, ex.getMessage());
            return Optional.empty();
        }
    }
}
