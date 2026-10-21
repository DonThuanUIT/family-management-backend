package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.CreateOnboardingBranchCommand;
import com.familyfund.genealogy.api.CreateOnboardingMemberCommand;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.identity.api.BranchJoinRequestService;
import com.familyfund.identity.api.dto.JoinRequestResponse;
import com.familyfund.identity.api.dto.RejectJoinRequest;
import com.familyfund.identity.api.dto.SubmitJoinRequest;
import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountRoleId;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.BranchJoinRequest;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import com.familyfund.identity.internal.entity.ProposalType;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import com.familyfund.identity.internal.repository.BranchJoinRequestRepository;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
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
    private final AccountRoleRepository accountRoleRepository;
    private final GenealogyQueryService genealogyQueryService;
    private final GenealogyMemberCommandService genealogyMemberCommandService;
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

    @Override
    public Page<JoinRequestResponse> getJoinRequests(
            JoinRequestApprovalStatus status,
            String branchId,
            Pageable pageable,
            Authentication authentication
    ) {
        String clanId = TenantContext.getClanId();
        JoinRequestApprovalStatus targetStatus = status != null ? status : JoinRequestApprovalStatus.Pending;

        boolean isClanWideAdmin = isClanWideAdmin(authentication);

        Page<BranchJoinRequest> page;
        if (isClanWideAdmin) {
            if (branchId != null && !branchId.isBlank()) {
                page = branchJoinRequestRepository.findWithAccountByClanIdAndBranchIdsAndApprovalStatus(
                        clanId, List.of(branchId.trim()), targetStatus, pageable);
            } else {
                page = branchJoinRequestRepository.findWithAccountByClanIdAndApprovalStatus(
                        clanId, targetStatus, pageable);
            }
        } else {
            List<String> managedBranchIds = resolveManagedBranches(clanId, authentication);
            if (managedBranchIds.isEmpty()) {
                return Page.empty(pageable);
            }

            if (branchId != null && !branchId.isBlank()) {
                String cleanBranchId = branchId.trim();
                if (!managedBranchIds.contains(cleanBranchId)) {
                    throw new BadRequestException("Bạn không có quyền quản lý chi nhánh: " + cleanBranchId);
                }
                page = branchJoinRequestRepository.findWithAccountByClanIdAndBranchIdsAndApprovalStatus(
                        clanId, List.of(cleanBranchId), targetStatus, pageable);
            } else {
                page = branchJoinRequestRepository.findWithAccountByClanIdAndBranchIdsAndApprovalStatus(
                        clanId, managedBranchIds, targetStatus, pageable);
            }
        }

        return page.map(req -> {
            String branchName = genealogyQueryService.getBranchName(clanId, req.getTargetBranchId()).orElse(null);
            return JoinRequestResponse.fromEntity(req, branchName);
        });
    }

    @Override
    @Transactional
    public JoinRequestResponse approveJoinRequest(String requestId, Authentication authentication) {
        String clanId = TenantContext.getClanId();
        String reviewerAccountId = resolveAccountId(authentication);

        BranchJoinRequest joinRequest = branchJoinRequestRepository
                .findWithAccountByRequestIdAndClanId(requestId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Yêu cầu gia nhập", "requestId", requestId));

        if (joinRequest.getApprovalStatus() != JoinRequestApprovalStatus.Pending) {
            throw new BusinessRuleException("Yêu cầu này đã được xử lý (trạng thái: " + joinRequest.getApprovalStatus() + ")");
        }

        verifyReviewerBranchAuthority(clanId, joinRequest.getTargetBranchId(), authentication);

        // 1. Cross-module call to genealogy module
        String createdMemberId;
        if (joinRequest.getProposalType() == ProposalType.ExistingBranchLeaf) {
            CreateOnboardingMemberCommand cmd = CreateOnboardingMemberCommand.builder()
                    .clanId(clanId)
                    .branchId(joinRequest.getTargetBranchId())
                    .fullName(joinRequest.getFullName())
                    .dateOfBirth(joinRequest.getDateOfBirth())
                    .gender(joinRequest.getGender())
                    .generation(joinRequest.getProposedGeneration())
                    .build();
            createdMemberId = genealogyMemberCommandService.createMemberForExistingBranch(cmd);
        } else {
            CreateOnboardingBranchCommand cmd = CreateOnboardingBranchCommand.builder()
                    .clanId(clanId)
                    .parentBranchId(joinRequest.getTargetBranchId())
                    .newBranchName(joinRequest.getProposedNewBranchName())
                    .memberFullName(joinRequest.getFullName())
                    .memberDateOfBirth(joinRequest.getDateOfBirth())
                    .memberGender(joinRequest.getGender())
                    .memberGeneration(joinRequest.getProposedGeneration())
                    .build();
            createdMemberId = genealogyMemberCommandService.createChildBranchAndMember(cmd);
        }

        // 2. Activate UserAccount and link memberId
        UserAccount account = joinRequest.getUserAccount();
        account.setMemberId(createdMemberId);
        account.setAccountStatus(AccountStatus.Active);
        userAccountRepository.save(account);

        // 3. Assign ROLE_MEMBER
        AccountRole memberRole = AccountRole.builder()
                .id(new AccountRoleId(account.getAccountId(), "ROLE_MEMBER"))
                .assignedAt(Instant.now())
                .build();
        accountRoleRepository.save(memberRole);

        // 4. Increment usedCount on InvitationCode
        invitationCodeRepository.findByInviteCodeAndIsActiveTrue(joinRequest.getInviteCode())
                .ifPresent(inv -> {
                    inv.setUsedCount((inv.getUsedCount() != null ? inv.getUsedCount() : 0) + 1);
                    invitationCodeRepository.save(inv);
                });

        // 5. Update BranchJoinRequest status
        joinRequest.setApprovalStatus(JoinRequestApprovalStatus.Approved);
        joinRequest.setReviewedByAccountId(reviewerAccountId);
        joinRequest.setReviewedAt(Instant.now());
        BranchJoinRequest updatedRequest = branchJoinRequestRepository.save(joinRequest);

        log.info("Đã phê duyệt yêu cầu gia nhập: requestId={}, memberId={}, accountId={}, reviewer={}",
                requestId, createdMemberId, account.getAccountId(), reviewerAccountId);

        String branchName = genealogyQueryService.getBranchName(clanId, joinRequest.getTargetBranchId()).orElse(null);
        return JoinRequestResponse.fromEntity(updatedRequest, branchName);
    }

    @Override
    @Transactional
    public JoinRequestResponse rejectJoinRequest(String requestId, RejectJoinRequest request, Authentication authentication) {
        String clanId = TenantContext.getClanId();
        String reviewerAccountId = resolveAccountId(authentication);

        BranchJoinRequest joinRequest = branchJoinRequestRepository
                .findWithAccountByRequestIdAndClanId(requestId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Yêu cầu gia nhập", "requestId", requestId));

        if (joinRequest.getApprovalStatus() != JoinRequestApprovalStatus.Pending) {
            throw new BusinessRuleException("Yêu cầu này đã được xử lý (trạng thái: " + joinRequest.getApprovalStatus() + ")");
        }

        verifyReviewerBranchAuthority(clanId, joinRequest.getTargetBranchId(), authentication);

        // 1. Deactivate temporary UserAccount
        UserAccount account = joinRequest.getUserAccount();
        account.setAccountStatus(AccountStatus.Deactivated);
        userAccountRepository.save(account);

        // 2. Update BranchJoinRequest status to Rejected with reason
        joinRequest.setApprovalStatus(JoinRequestApprovalStatus.Rejected);
        joinRequest.setRejectionReason(request.rejectionReason().trim());
        joinRequest.setReviewedByAccountId(reviewerAccountId);
        joinRequest.setReviewedAt(Instant.now());
        BranchJoinRequest updatedRequest = branchJoinRequestRepository.save(joinRequest);

        log.info("Đã từ chối yêu cầu gia nhập: requestId={}, accountId={}, reviewer={}, reason={}",
                requestId, account.getAccountId(), reviewerAccountId, request.rejectionReason());

        String branchName = genealogyQueryService.getBranchName(clanId, joinRequest.getTargetBranchId()).orElse(null);
        return JoinRequestResponse.fromEntity(updatedRequest, branchName);
    }

    private boolean isClanWideAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLAN_HEAD") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String resolveAccountId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        return "ACC_ANONYMOUS";
    }

    private List<String> resolveManagedBranches(String clanId, Authentication authentication) {
        String accountId = resolveAccountId(authentication);
        return userAccountRepository.findById(accountId)
                .map(UserAccount::getMemberId)
                .filter(memberId -> !memberId.isBlank())
                .map(memberId -> genealogyQueryService.getBranchIdsManagedByMember(clanId, memberId))
                .orElse(Collections.emptyList());
    }

    private void verifyReviewerBranchAuthority(String clanId, String targetBranchId, Authentication authentication) {
        if (isClanWideAdmin(authentication)) {
            return;
        }

        List<String> managedBranchIds = resolveManagedBranches(clanId, authentication);
        if (!managedBranchIds.contains(targetBranchId)) {
            throw new BadRequestException("Bạn không có quyền phê duyệt/từ chối yêu cầu thuộc chi nhánh này");
        }
    }
}
