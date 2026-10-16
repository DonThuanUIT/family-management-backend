package com.familyfund.modules.genealogy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @Column(name = "member_id", length = 20, nullable = false)
    private String memberId;

    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 10, nullable = false)
    private String gender;

    @Column(name = "generation", nullable = false)
    private Integer generation;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "Living";

    @Column(name = "death_date_lunar", length = 50)
    private String deathDateLunar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private FamilyBranch branch;

    @Column(name = "is_placeholder")
    @Builder.Default
    private Boolean isPlaceholder = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
