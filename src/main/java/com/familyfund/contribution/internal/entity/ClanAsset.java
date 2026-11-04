package com.familyfund.contribution.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping table `clan_assets`.
 * Represents accumulated clan assets stored at the ancestral hall, transitioned from in-kind contributions after events.
 */
@Entity
@Table(name = "clan_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClanAsset {

    @Id
    @Column(name = "asset_id", length = 20, nullable = false, updatable = false)
    private String assetId;

    @Column(name = "asset_name", length = 100, nullable = false)
    private String assetName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit", length = 20, nullable = false)
    private String unit;

    @Column(name = "assessed_value", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal assessedValue = BigDecimal.ZERO;

    @Column(name = "storage_location", length = 255)
    private String storageLocation;

    @Column(name = "origin_contribution_id", length = 20)
    private String originContributionId;

    @Column(name = "clan_id", length = 20, nullable = false)
    private String clanId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
