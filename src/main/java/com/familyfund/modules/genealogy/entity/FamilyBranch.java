package com.familyfund.modules.genealogy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "family_branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FamilyBranch {

    @Id
    @Column(name = "branch_id", length = 20, nullable = false)
    private String branchId;

    @Column(name = "clan_name", length = 150)
    private String clanName;

    @Column(name = "branch_name", length = 100, nullable = false)
    private String branchName;

    @Column(name = "generation_level", nullable = false)
    private Integer generationLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_branch_id")
    private FamilyBranch parentBranch;

    @OneToMany(mappedBy = "parentBranch", cascade = CascadeType.ALL)
    @Builder.Default
    private List<FamilyBranch> childBranches = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_id")
    private Member representative;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Member> members = new ArrayList<>();
}
