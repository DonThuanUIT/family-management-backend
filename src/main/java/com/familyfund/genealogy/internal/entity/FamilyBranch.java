package com.familyfund.genealogy.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
@ToString(exclude = {"parentBranch", "childBranches", "members", "representative"})
public class FamilyBranch {

    @Id
    @Column(name = "branch_id", length = 20, nullable = false)
    private String branchId;

    @Column(name = "clan_id", length = 20, nullable = false)
    private String clanId;

    @Column(name = "branch_name", length = 100, nullable = false)
    private String branchName;

    @Column(name = "generation_level", nullable = false)
    private Integer generationLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_branch_id")
    private FamilyBranch parentBranch;

    @OneToMany(mappedBy = "parentBranch")
    @Builder.Default
    private List<FamilyBranch> childBranches = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_id")
    private Member representative;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "branch")
    @Builder.Default
    private List<Member> members = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
