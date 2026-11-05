package com.familyfund.audit.repository;

import com.familyfund.event.internal.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemAuditLogRepository extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {

    Optional<AuditLog> findByAuditIdAndClanId(String auditId, String clanId);
}
