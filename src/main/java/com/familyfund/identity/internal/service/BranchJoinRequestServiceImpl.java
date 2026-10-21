package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.identity.api.BranchJoinRequestService;
import com.familyfund.identity.api.dto.JoinRequestResponse;
import com.familyfund.identity.api.dto.SubmitJoinRequest;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.BranchJoinRequest;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import com.familyfund.identity.internal.entity.ProposalType;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.BranchJoinRequestRepository;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service implementation for managing branch join requests (US-17).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchJoinRequestServiceImpl implements BranchJoinRequestService {

    private final InvitationCodeRepository invitationCodeRepository;
    private final UserAccountRepository userAccountRepository;
    private final BranchJoinRequestRepository branchJoinRequestRepository;
    private final GenealogyQueryService genealogyQueryService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public JoinRequestResponse submitJoinRequest(String inviteCode, SubmitJoinRequest request) {
        // 1. Validate Invite Code
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BadRequestException("Mã mời không được để trống");
        }

        InvitationCode invitation = invitationCodeRepository.findByInviteCodeAndIsActiveTrue(inviteCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Mã mời", "inviteCode", inviteCode));

        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Mã mời đã hết hạn sử dụng");
        }

        if (invitation.getMaxUses() != null && invitation.getUsedCount() != null && invitation.getUsedCount() >= invitation.getMaxUses()) {
            throw new BadRequestException("Mã mời đã hết lượt sử dụng");
        }

        String clanId = invitation.getClanId();

        // 2. Validate User Account uniqueness
        String trimmedPhone = request.phoneNumber().trim();
        if (userAccountRepository.existsByPhoneNumber(trimmedPhone)) {
            throw new BadRequestException("Số điện thoại đã được đăng ký trong hệ thống");
        }

        String trimmedEmail = (request.email() != null && !request.email().isBlank())
                ? request.email().trim().toLowerCase()
                : null;
        if (trimmedEmail != null && userAccountRepository.existsByEmail(trimmedEmail)) {
            throw new BadRequestException("Email đã được đăng ký trong hệ thống");
        }

        // 3. Validate Genealogy Position Proposal
        String targetBranchId = request.targetBranchId().trim();
        if (!genealogyQueryService.existsBranch(clanId, targetBranchId)) {
            throw new ResourceNotFoundException("Nhánh gia phả", "branchId", targetBranchId);
        }

        if (invitation.getTargetBranchId() != null && !invitation.getTargetBranchId().isBlank()) {
            if (!invitation.getTargetBranchId().trim().equals(targetBranchId)) {
                throw new BadRequestException("Mã mời này chỉ áp dụng cho nhánh mục tiêu: " + invitation.getTargetBranchId());
            }
        }

        if (request.proposedGeneration() == null || request.proposedGeneration() < 1) {
            throw new BadRequestException("Đời thứ đề xuất phải lớn hơn hoặc bằng 1");
        }

        String proposedNewBranchName = null;
        if (request.proposalType() == ProposalType.NewChildBranch) {
            if (request.proposedNewBranchName() == null || request.proposedNewBranchName().trim().isBlank()) {
                throw new BadRequestException("Tên chi nhánh mới không được để trống khi đề xuất tạo nhánh mới");
            }
            proposedNewBranchName = request.proposedNewBranchName().trim();
        }

        // 4. Create and persist UserAccount in PendingApproval state
        String accountId = "ACC_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String encodedPassword = passwordEncoder.encode(request.password());

        UserAccount userAccount = UserAccount.builder()
                .accountId(accountId)
                .phoneNumber(trimmedPhone)
                .email(trimmedEmail)
                .passwordHash(encodedPassword)
                .accountStatus(AccountStatus.PendingApproval)
                .memberId(null)
                .build();

        UserAccount savedAccount = userAccountRepository.save(userAccount);

        // 5. Create and persist BranchJoinRequest in Pending state
        String requestId = "REQ_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        BranchJoinRequest joinRequest = BranchJoinRequest.builder()
                .requestId(requestId)
                .clanId(clanId)
                .userAccount(savedAccount)
                .inviteCode(invitation.getInviteCode())
                .fullName(request.fullName().trim())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender().trim())
                .proposalType(request.proposalType())
                .targetBranchId(targetBranchId)
                .proposedNewBranchName(proposedNewBranchName)
                .proposedGeneration(request.proposedGeneration())
                .relationshipNote(request.relationshipNote() != null ? request.relationshipNote().trim() : null)
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .build();

        BranchJoinRequest savedRequest = branchJoinRequestRepository.save(joinRequest);
        log.info("Đã tạo yêu cầu gia nhập mới: requestId={}, accountId={}, clanId={}, targetBranchId={}",
                requestId, accountId, clanId, targetBranchId);

        String targetBranchName = genealogyQueryService.getBranchName(clanId, targetBranchId).orElse(null);
        return JoinRequestResponse.fromEntity(savedRequest, targetBranchName);
    }
}
