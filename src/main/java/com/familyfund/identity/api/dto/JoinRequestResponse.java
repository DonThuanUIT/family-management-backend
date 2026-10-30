package com.familyfund.identity.api.dto;

import com.familyfund.identity.internal.entity.BranchJoinRequest;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import com.familyfund.identity.internal.entity.ProposalType;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response payload representing details of a branch join request (US-17).
 */
@Builder
public record JoinRequestResponse(
        String requestId,
        String accountId,
        String clanId,
        String inviteCode,
        String fullName,
        String phoneNumber,
        String email,
        LocalDate dateOfBirth,
        String gender,
        ProposalType proposalType,
        String targetBranchId,
        String targetBranchName,
        String proposedNewBranchName,
        Integer proposedGeneration,
        String relationshipNote,
        JoinRequestApprovalStatus approvalStatus,
        String reviewedByAccountId,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt
) {
    public static JoinRequestResponse fromEntity(BranchJoinRequest entity, String targetBranchName) {
        if (entity == null) {
            return null;
        }
        String phone = entity.getUserAccount() != null ? entity.getUserAccount().getPhoneNumber() : null;
        String email = entity.getUserAccount() != null ? entity.getUserAccount().getEmail() : null;

        return JoinRequestResponse.builder()
                .requestId(entity.getRequestId())
                .accountId(entity.getAccountId())
                .clanId(entity.getClanId())
                .inviteCode(entity.getInviteCode())
                .fullName(entity.getFullName())
                .phoneNumber(phone)
                .email(email)
                .dateOfBirth(entity.getDateOfBirth())
                .gender(entity.getGender())
                .proposalType(entity.getProposalType())
                .targetBranchId(entity.getTargetBranchId())
                .targetBranchName(targetBranchName)
                .proposedNewBranchName(entity.getProposedNewBranchName())
                .proposedGeneration(entity.getProposedGeneration())
                .relationshipNote(entity.getRelationshipNote())
                .approvalStatus(entity.getApprovalStatus())
                .reviewedByAccountId(entity.getReviewedByAccountId())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .reviewedAt(entity.getReviewedAt())
                .build();
    }
}

