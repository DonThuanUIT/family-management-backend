package com.familyfund.event.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Entity mapping table `audit_logs`.
 * Stores immutable audit history for system actions, role assignments, and data mutations.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuditLog {

    @Id
    @Column(name = "audit_id", length = 20, nullable = false)
    private String auditId;

    @Column(name = "clan_id", length = 20, nullable = false)
    private String clanId;

    @Column(name = "actor_account_id", length = 20, nullable = false)
    private String actorAccountId;

    @Column(name = "target_member_id", length = 20)
    private String targetMemberId;

    @Column(name = "target_account_id", length = 20)
    private String targetAccountId;

    @Column(name = "action_type", length = 30, nullable = false)
    private String actionType;

    @Column(name = "entity_type", length = 30, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 30)
    private String entityId;

    @Column(name = "role_code", length = 30)
    private String roleCode;

    @Column(name = "scope_type", length = 20)
    private String scopeType;

    @Column(name = "scope_id", length = 20)
    private String scopeId;

    @Column(name = "scope_name", length = 150)
    private String scopeName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
