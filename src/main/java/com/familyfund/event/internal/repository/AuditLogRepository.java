package com.familyfund.event.internal.repository;

import com.familyfund.core.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    List<AuditLog> findByClanIdOrderByCreatedAtDesc(String clanId);

    List<AuditLog> findByClanIdAndTargetMemberIdOrderByCreatedAtDesc(String clanId, String targetMemberId);

    List<AuditLog> findByClanIdAndScopeTypeAndScopeIdOrderByCreatedAtDesc(String clanId, String scopeType, String scopeId);
}
