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
public class JoinRequestAuditSnapshotProvider implements AuditSnapshotProvider {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public AuditEntityType supports() {
        return AuditEntityType.JOIN_REQUEST;
    }

    @Override
    public Optional<Map<String, Object>> snapshot(String entityId, String clanId) {
        if (entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        try {
            String sql = "SELECT r.request_id, r.clan_id, r.target_branch_id, r.full_name, " +
                    "u.phone_number, u.email, r.proposed_generation, r.gender, " +
                    "r.proposal_type, r.proposed_new_branch_name, r.approval_status, " +
                    "r.relationship_note, r.reviewed_by_account_id, r.rejection_reason " +
                    "FROM branch_join_requests r " +
                    "LEFT JOIN user_accounts u ON r.account_id = u.account_id " +
                    "WHERE r.request_id = ?";
            return jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("requestId", rs.getString("request_id"));
                    map.put("clanId", rs.getString("clan_id"));
                    map.put("targetBranchId", rs.getString("target_branch_id"));
                    map.put("fullName", rs.getString("full_name"));
                    map.put("phoneNumber", rs.getString("phone_number"));
                    map.put("email", rs.getString("email"));
                    map.put("proposedGeneration", rs.getInt("proposed_generation"));
                    map.put("gender", rs.getString("gender"));
                    map.put("proposalType", rs.getString("proposal_type"));
                    map.put("proposedNewBranchName", rs.getString("proposed_new_branch_name"));
                    map.put("approvalStatus", rs.getString("approval_status"));
                    map.put("relationshipNote", rs.getString("relationship_note"));
                    map.put("reviewedByAccountId", rs.getString("reviewed_by_account_id"));
                    map.put("rejectionReason", rs.getString("rejection_reason"));
                    return Optional.of(map);
                }
                return Optional.empty();
            }, entityId.trim());
        } catch (Exception ex) {
            log.warn("Failed to capture join request snapshot for requestId={}: {}", entityId, ex.getMessage());
            return Optional.empty();
        }
    }
}
