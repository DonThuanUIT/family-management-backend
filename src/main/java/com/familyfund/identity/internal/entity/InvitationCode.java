package com.familyfund.identity.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entity mapping table `invitation_codes`.
 * Stores general clan invite links (US-16) and position link codes (US-18, US-19).
 */
@Entity
@Table(name = "invitation_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class InvitationCode {

    @Id
    @Column(name = "invite_code", length = 30, nullable = false)
    private String inviteCode;

    @Column(name = "clan_id", length = 20)
    private String clanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "invite_type", length = 30, nullable = false)
    private InviteType inviteType;

    @Column(name = "shareable_url", length = 255)
    private String shareableUrl;

    @Column(name = "target_branch_id", length = 20)
    private String targetBranchId;

    @Column(name = "target_member_id", length = 20, unique = true)
    private String targetMemberId;

    @Column(name = "max_uses")
    @Builder.Default
    private Integer maxUses = 1;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private Integer usedCount = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by_account_id", length = 20, nullable = false)
    private String createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.usedCount == null) {
            this.usedCount = 0;
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.maxUses == null) {
            this.maxUses = 1;
        }
    }
}

