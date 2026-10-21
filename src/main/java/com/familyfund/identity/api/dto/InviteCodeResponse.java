package com.familyfund.identity.api.dto;

import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response payload returning invitation code details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteCodeResponse {

    private String inviteCode;
    private String clanId;
    private String clanName;
    private InviteType inviteType;
    private String shareableUrl;
    private String targetBranchId;
    private String targetBranchName;
    private String targetMemberId;
    private Integer maxUses;
    private Integer usedCount;
    private Instant expiresAt;
    private Boolean isActive;
    private String createdByAccountId;
    private Instant createdAt;

    public static InviteCodeResponse fromEntity(InvitationCode entity) {
        if (entity == null) {
            return null;
        }
        return InviteCodeResponse.builder()
                .inviteCode(entity.getInviteCode())
                .clanId(entity.getClanId())
                .inviteType(entity.getInviteType())
                .shareableUrl(entity.getShareableUrl())
                .targetBranchId(entity.getTargetBranchId())
                .targetMemberId(entity.getTargetMemberId())
                .maxUses(entity.getMaxUses())
                .usedCount(entity.getUsedCount())
                .expiresAt(entity.getExpiresAt())
                .isActive(entity.getIsActive())
                .createdByAccountId(entity.getCreatedByAccountId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
