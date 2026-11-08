package com.familyfund.core.audit.service;

import com.familyfund.core.audit.dto.AuditLogPageResponse;
import com.familyfund.core.audit.dto.SystemAuditLogResponse;
import com.familyfund.core.audit.repository.SystemAuditLogRepository;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.AssignableRoleCode;
import com.familyfund.core.audit.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditQueryServiceImpl implements AuditQueryService {

    private final SystemAuditLogRepository auditLogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public AuditLogPageResponse getAuditLogs(
            int page,
            int size,
            String actorId,
            List<String> actionTypes,
            List<String> entityTypes,
            String entityId,
            String from,
            String to
    ) {
        validateCallerAuthorization();
        String clanId = TenantContext.getClanId();
        if (clanId == null || clanId.isBlank()) {
            clanId = "CLAN_DEFAULT";
        }

        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);

        Instant fromInstant = parseFromDate(from);
        Instant toInstant = parseToDate(to);

        final String targetClanId = clanId;
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clanId"), targetClanId));

            if (actorId != null && !actorId.isBlank()) {
                predicates.add(cb.equal(root.get("actorAccountId"), actorId.trim()));
            }

            if (actionTypes != null && !actionTypes.isEmpty()) {
                List<String> upperActions = actionTypes.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toUpperCase)
                        .toList();
                if (!upperActions.isEmpty()) {
                    predicates.add(root.get("actionType").in(upperActions));
                }
            }

            if (entityTypes != null && !entityTypes.isEmpty()) {
                List<String> upperEntities = entityTypes.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toUpperCase)
                        .toList();
                if (!upperEntities.isEmpty()) {
                    predicates.add(root.get("entityType").in(upperEntities));
                }
            }

            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }

            if (fromInstant != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
            }

            if (toInstant != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toInstant));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditPage = auditLogRepository.findAll(spec, pageRequest);

        // Batch resolve actor names and target member names
        Map<String, String> actorNameMap = resolveActorNames(auditPage.getContent());
        Map<String, String> targetMemberNameMap = resolveTargetMemberNames(auditPage.getContent());

        List<SystemAuditLogResponse> responseItems = auditPage.getContent().stream()
                .map(logEntry -> mapToResponse(logEntry, actorNameMap, targetMemberNameMap))
                .toList();

        return new AuditLogPageResponse(
                responseItems,
                auditPage.getNumber(),
                auditPage.getSize(),
                auditPage.getTotalElements(),
                auditPage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SystemAuditLogResponse getAuditLogDetail(String auditId) {
        validateCallerAuthorization();
        String clanId = TenantContext.getClanId();
        if (clanId == null || clanId.isBlank()) {
            clanId = "CLAN_DEFAULT";
        }

        AuditLog logEntry = auditLogRepository.findByAuditIdAndClanId(auditId.trim(), clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhật ký kiểm toán", "auditId", auditId));

        Map<String, String> actorNameMap = resolveActorNames(List.of(logEntry));
        Map<String, String> targetMemberNameMap = resolveTargetMemberNames(List.of(logEntry));

        return mapToResponse(logEntry, actorNameMap, targetMemberNameMap);
    }

    private void validateCallerAuthorization() {
        if (!currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD) && !currentUserProvider.hasAuthority(SystemRoles.ADMIN)) {
            throw new AccessDeniedException("Chỉ Trưởng tộc hoặc Quản trị hệ thống mới có quyền tra cứu Sổ bộ Kiểm toán");
        }
    }

    private SystemAuditLogResponse mapToResponse(
            AuditLog logEntry,
            Map<String, String> actorNameMap,
            Map<String, String> targetMemberNameMap
    ) {
        String actorFullName = actorNameMap.getOrDefault(logEntry.getActorAccountId(), logEntry.getActorAccountId());
        String targetMemberName = logEntry.getTargetMemberId() != null
                ? targetMemberNameMap.getOrDefault(logEntry.getTargetMemberId(), logEntry.getTargetMemberId())
                : null;

        String roleDisplayName = null;
        if (logEntry.getRoleCode() != null) {
            AssignableRoleCode code = AssignableRoleCode.fromCode(logEntry.getRoleCode());
            roleDisplayName = code != null ? code.getDisplayName() : logEntry.getRoleCode();
        }

        return new SystemAuditLogResponse(
                logEntry.getAuditId(),
                logEntry.getClanId(),
                logEntry.getActorAccountId(),
                actorFullName,
                logEntry.getActionType(),
                logEntry.getEntityType(),
                logEntry.getEntityId(),
                logEntry.getRoleCode(),
                roleDisplayName,
                logEntry.getScopeType(),
                logEntry.getScopeId(),
                logEntry.getScopeName(),
                logEntry.getTargetMemberId(),
                targetMemberName,
                logEntry.getTargetAccountId(),
                logEntry.getDescription(),
                logEntry.getOldValue(),
                logEntry.getNewValue(),
                logEntry.getIpAddress(),
                logEntry.getCreatedAt()
        );
    }

    private Map<String, String> resolveActorNames(List<AuditLog> logs) {
        Set<String> actorIds = new HashSet<>();
        for (AuditLog l : logs) {
            if (l.getActorAccountId() != null && !l.getActorAccountId().isBlank()) {
                actorIds.add(l.getActorAccountId().trim());
            }
        }
        if (actorIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = "SELECT u.account_id, COALESCE(m.full_name, u.phone_number, u.email, u.account_id) AS display_name " +
                "FROM user_accounts u LEFT JOIN members m ON u.member_id = m.member_id " +
                "WHERE u.account_id IN (:ids)";

        MapSqlParameterSource params = new MapSqlParameterSource("ids", actorIds);
        Map<String, String> result = new HashMap<>();
        namedParameterJdbcTemplate.query(sql, params, rs -> {
            result.put(rs.getString("account_id"), rs.getString("display_name"));
        });
        return result;
    }

    private Map<String, String> resolveTargetMemberNames(List<AuditLog> logs) {
        Set<String> memberIds = new HashSet<>();
        for (AuditLog l : logs) {
            if (l.getTargetMemberId() != null && !l.getTargetMemberId().isBlank()) {
                memberIds.add(l.getTargetMemberId().trim());
            }
        }
        if (memberIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = "SELECT member_id, full_name FROM members WHERE member_id IN (:ids)";
        MapSqlParameterSource params = new MapSqlParameterSource("ids", memberIds);
        Map<String, String> result = new HashMap<>();
        namedParameterJdbcTemplate.query(sql, params, rs -> {
            result.put(rs.getString("member_id"), rs.getString("full_name"));
        });
        return result;
    }

    private Instant parseFromDate(String from) {
        if (from == null || from.isBlank()) return null;
        try {
            return Instant.parse(from.trim());
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(from.trim());
                return date.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e) {
                log.warn("Invalid from date format: {}", from);
                return null;
            }
        }
    }

    private Instant parseToDate(String to) {
        if (to == null || to.isBlank()) return null;
        try {
            return Instant.parse(to.trim());
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(to.trim());
                // Inclusive end of day: 23:59:59.999Z
                return date.atTime(23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                log.warn("Invalid to date format: {}", to);
                return null;
            }
        }
    }
}
