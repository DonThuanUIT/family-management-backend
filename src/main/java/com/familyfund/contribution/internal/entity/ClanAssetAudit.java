package com.familyfund.contribution.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity mapping table `clan_asset_audits`.
 * Tracks historical audit records, condition changes, and physical counts of clan assets (US-52).
 */
@Entity
@Table(name = "clan_asset_audits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClanAssetAudit {

    @Id
    @Column(name = "audit_id", length = 20, nullable = false, updatable = false)
    private String auditId;

    @Column(name = "asset_id", length = 20, nullable = false)
    private String assetId;

    @Column(name = "audit_date", nullable = false)
    private Instant auditDate;

    @Column(name = "previous_quantity", nullable = false)
    private Integer previousQuantity;

    @Column(name = "actual_quantity", nullable = false)
    private Integer actualQuantity;

    @Column(name = "previous_condition", length = 50)
    private String previousCondition;

    @Column(name = "condition_status", length = 50, nullable = false)
    private String conditionStatus;

    @Column(name = "storage_location", length = 255)
    private String storageLocation;

    @Column(name = "auditor_account_id", length = 20, nullable = false)
    private String auditorAccountId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (auditDate == null) {
            auditDate = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
