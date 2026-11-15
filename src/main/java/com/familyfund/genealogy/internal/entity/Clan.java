package com.familyfund.genealogy.internal.entity;

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

import java.time.Instant;

/**
 * Entity mapping table `clans`.
 * Represents a distinct family lineage / clan tenant in the multi-tenancy model.
 */
@Entity
@Table(name = "clans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Clan {

    @Id
    @Column(name = "clan_id", length = 20, nullable = false)
    private String clanId;

    @Column(name = "clan_name", length = 150, nullable = false)
    private String clanName;

    @Column(name = "ancestor_name", length = 100)
    private String ancestorName;

    @Column(name = "origin_location", length = 255)
    private String originLocation;

    @Column(name = "established_year")
    private Integer establishedYear;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "bank_bin", length = 10)
    private String bankBin;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_name", length = 150)
    private String bankAccountName;

    @Column(name = "qr_template", length = 20)
    @Builder.Default
    private String qrTemplate = "compact2";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

