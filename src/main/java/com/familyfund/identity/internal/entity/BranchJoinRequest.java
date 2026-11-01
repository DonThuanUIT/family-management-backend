package com.familyfund.identity.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Entity mapping table `branch_join_requests`.
 * Stores self-onboarding join proposals from external users via general clan invite codes (US-17).
 */
@Entity
@Table(name = "branch_join_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "userAccount")
public class BranchJoinRequest {

    @Id
    @Column(name = "request_id", length = 20, nullable = false)
    private String requestId;

    @Column(name = "clan_id", length = 20, nullable = false)
    private String clanId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private UserAccount userAccount;

    @Column(name = "invite_code", length = 30, nullable = false)
    private String inviteCode;

    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 10, nullable = false)
    private String gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_type", length = 30, nullable = false)
    private ProposalType proposalType;

    @Column(name = "target_branch_id", length = 20, nullable = false)
    private String targetBranchId;

    @Column(name = "proposed_new_branch_name", length = 100)
    private String proposedNewBranchName;

    @Column(name = "proposed_generation", nullable = false)
    private Integer proposedGeneration;

    @Column(name = "relationship_note", length = 255)
    private String relationshipNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20, nullable = false)
    @Builder.Default
    private JoinRequestApprovalStatus approvalStatus = JoinRequestApprovalStatus.Pending;

    @Column(name = "reviewed_by_account_id", length = 20)
    private String reviewedByAccountId;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.approvalStatus == null) {
            this.approvalStatus = JoinRequestApprovalStatus.Pending;
        }
    }

    public String getAccountId() {
        return userAccount != null ? userAccount.getAccountId() : null;
    }
}

