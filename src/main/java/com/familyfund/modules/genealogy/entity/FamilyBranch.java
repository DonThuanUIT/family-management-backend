package com.familyfund.modules.genealogy.entity;

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
 * Entity mapping table `family_branches`.
 * Represents a hierarchical Chi/Nhánh in the family genealogy tree.
 */
@Entity
@Table(name = "family_branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FamilyBranch {

    @Id
    @Column(name = "branch_id", length = 20, nullable = false)
    private String branchId;

    @Column(name = "clan_id", length = 20)
    private String clanId;

    @Column(name = "clan_name", length = 150)
    private String clanName;

    @Column(name = "branch_name", length = 100, nullable = false)
    private String branchName;

    @Column(name = "generation_level", nullable = false)
    private Integer generationLevel;

    @Column(name = "parent_branch_id", length = 20)
    private String parentBranchId;

    @Column(name = "representative_id", length = 20)
    private String representativeId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

