package com.familyfund.genealogy.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Entity mapping table `members` (Flyway V1), column-for-column.
 * Tenant scoping is derived through {@link FamilyBranch#getClanId()} since `members` has no clan_id column.
 */
@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "branch")
public class Member {

    public static final String STATUS_LIVING = "Living";

    // member_id VARCHAR(20) PRIMARY KEY
    @Id
    @Column(name = "member_id", length = 20, nullable = false, updatable = false)
    private String memberId;

    // full_name VARCHAR(100) NOT NULL
    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    // date_of_birth DATE
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    // gender VARCHAR(10) NOT NULL CHECK (gender IN ('Male', 'Female', 'Other'))
    @Column(name = "gender", length = 10, nullable = false)
    private String gender;

    // generation INT NOT NULL CHECK (generation >= 1)
    @Column(name = "generation", nullable = false)
    private Integer generation;

    // status VARCHAR(20) NOT NULL DEFAULT 'Living' CHECK (status IN ('Living', 'Deceased'))
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = STATUS_LIVING;

    // death_date_lunar VARCHAR(50)
    @Column(name = "death_date_lunar", length = 50)
    private String deathDateLunar;

    // branch_id VARCHAR(20) NOT NULL REFERENCES family_branches(branch_id) ON DELETE RESTRICT
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private FamilyBranch branch;

    // is_placeholder BOOLEAN DEFAULT FALSE
    @Column(name = "is_placeholder")
    @Builder.Default
    private Boolean isPlaceholder = false;

    // created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        // Mirror DB defaults so explicit NULLs from the builder never override them
        if (this.status == null) {
            this.status = STATUS_LIVING;
        }
        if (this.isPlaceholder == null) {
            this.isPlaceholder = false;
        }
    }
}
