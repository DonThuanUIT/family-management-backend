package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.OnboardingBranchTreeNode;
import com.familyfund.identity.api.InvitationService;
import com.familyfund.identity.api.dto.CreateGeneralInviteRequest;
import com.familyfund.identity.api.dto.InviteCodeResponse;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.util.InviteCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationServiceImpl implements InvitationService {

    private static final int DEFAULT_MAX_USES = 100;
    private static final int DEFAULT_VALIDITY_DAYS = 30;
    private static final int MAX_GENERATION_ATTEMPTS = 3;

    private final InvitationCodeRepository invitationCodeRepository;
    private final GenealogyQueryService genealogyQueryService;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Override
    @Transactional
    public InviteCodeResponse createGeneralClanInvite(CreateGeneralInviteRequest request, String currentAccountId) {
        if (currentAccountId == null || currentAccountId.isBlank()) {
            throw new BadRequestException("Tài khoản người tạo không hợp lệ");
        }

        String clanId = TenantContext.getClanId();

        // 1. Kiểm tra tồn tại của Target Branch qua Genealogy Public Query Service theo đúng clanId
        String targetBranchId = null;
        if (request.getTargetBranchId() != null && !request.getTargetBranchId().isBlank()) {
            String trimmedBranchId = request.getTargetBranchId().trim();
            if (!genealogyQueryService.existsBranch(clanId, trimmedBranchId)) {
                throw new ResourceNotFoundException("Nhánh gia đình", "branchId", trimmedBranchId);
            }
            targetBranchId = trimmedBranchId;
        }

        // 2. Tính toán ngày hết hạn (Ưu tiên expiresAt > validityDays > mặc định 30 ngày)
        Instant expiresAt;
        if (request.getExpiresAt() != null) {
            expiresAt = request.getExpiresAt();
        } else if (request.getValidityDays() != null && request.getValidityDays() > 0) {
            expiresAt = Instant.now().plus(request.getValidityDays(), ChronoUnit.DAYS);
        } else {
            expiresAt = Instant.now().plus(DEFAULT_VALIDITY_DAYS, ChronoUnit.DAYS);
        }

        int maxUses = (request.getMaxUses() != null && request.getMaxUses() > 0)
                ? request.getMaxUses()
                : DEFAULT_MAX_USES;

        // 3. Sinh mã độc nhất với cơ chế Collision Retry
        String inviteCode = generateUniqueInviteCode();
        String shareableUrl = inviteCodeGenerator.buildShareableUrl(inviteCode);

        // 4. Lưu Entity vào CSDL
        InvitationCode entity = InvitationCode.builder()
                .inviteCode(inviteCode)
                .clanId(clanId)
                .inviteType(InviteType.GeneralClanInvite)
                .shareableUrl(shareableUrl)
                .targetBranchId(targetBranchId)
                .targetMemberId(null) // Luôn null đối với GeneralClanInvite
                .maxUses(maxUses)
                .usedCount(0)
                .expiresAt(expiresAt)
                .isActive(true)
                .createdByAccountId(currentAccountId)
                .build();

        InvitationCode saved = invitationCodeRepository.save(entity);
        log.info("Đã tạo mã mời chung mới: code={}, clanId={}, maxUses={}, expiresAt={}",
                inviteCode, clanId, maxUses, expiresAt);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivateInviteCode(String inviteCode, String currentAccountId) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BadRequestException("Mã mời không được để trống");
        }

        String clanId = TenantContext.getClanId();
        InvitationCode invitation = invitationCodeRepository
                .findByClanIdAndInviteCode(clanId, inviteCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Mã mời", "inviteCode", inviteCode));

        invitation.setIsActive(false);
        invitationCodeRepository.save(invitation);
        log.info("Đã vô hiệu hóa mã mời: code={}, clanId={}, bởi accountId={}", inviteCode, clanId, currentAccountId);
    }

    @Override
    public List<InviteCodeResponse> getActiveGeneralInvites() {
        String clanId = TenantContext.getClanId();
        return invitationCodeRepository
                .findAllByClanIdAndInviteTypeAndIsActiveTrueOrderByCreatedAtDesc(clanId, InviteType.GeneralClanInvite)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public InviteCodeResponse getInviteByCode(String inviteCode) {
        InvitationCode invitation = validateAndGetActiveInvite(inviteCode);
        return toResponse(invitation);
    }

    @Override
    public OnboardingBranchTreeNode getOnboardingBranchTree(String inviteCode) {
        InvitationCode invitation = validateAndGetActiveInvite(inviteCode);
        return genealogyQueryService.getOnboardingBranchTree(invitation.getClanId(), invitation.getTargetBranchId());
    }

    private InvitationCode validateAndGetActiveInvite(String inviteCode) {
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

        return invitation;
    }

    private InviteCodeResponse toResponse(InvitationCode entity) {
        InviteCodeResponse response = InviteCodeResponse.fromEntity(entity);
        if (entity.getClanId() != null) {
            genealogyQueryService.getClanName(entity.getClanId()).ifPresent(response::setClanName);
        }
        if (entity.getClanId() != null && entity.getTargetBranchId() != null) {
            genealogyQueryService.getBranchName(entity.getClanId(), entity.getTargetBranchId()).ifPresent(response::setTargetBranchName);
        }
        return response;
    }

    private String generateUniqueInviteCode() {
        int attempts = 0;
        String code;
        do {
            if (attempts++ >= MAX_GENERATION_ATTEMPTS) {
                log.error("Không thể sinh mã mời duy nhất sau {} lần thử", MAX_GENERATION_ATTEMPTS);
                throw new IllegalStateException("Hệ thống bận, vui lòng thử lại sau ít phút");
            }
            code = inviteCodeGenerator.generateCode();
        } while (invitationCodeRepository.existsByInviteCode(code));

        return code;
    }
}
