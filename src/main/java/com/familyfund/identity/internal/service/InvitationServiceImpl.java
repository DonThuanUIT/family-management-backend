package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.OnboardingBranchTreeNode;
import com.familyfund.identity.api.InvitationService;
import com.familyfund.identity.api.dto.CreateGeneralInviteRequest;
import com.familyfund.identity.api.dto.InviteCodeResponse;
import com.familyfund.identity.api.dto.UpdateInvitationRequest;
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
        InvitationCode invitation = loadClanInvite(inviteCode);
        invitation.setIsActive(false);
        invitationCodeRepository.save(invitation);
        log.info("Đã vô hiệu hóa mã mời: code={}, clanId={}, bởi accountId={}",
                invitation.getInviteCode(), invitation.getClanId(), currentAccountId);
    }

    @Override
    @Transactional
    public InviteCodeResponse updateInviteCode(String inviteCode, UpdateInvitationRequest request,
                                               String currentAccountId) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu cập nhật mã mời không được để trống");
        }
        boolean touchesExpiry = request.getExpiresAt() != null || request.getValidityDays() != null;
        if (request.getIsActive() == null && !touchesExpiry && request.getMaxUses() == null) {
            throw new BadRequestException(
                    "Cần cung cấp ít nhất một trường cần cập nhật: isActive, expiresAt/validityDays hoặc maxUses");
        }

        InvitationCode invitation = loadClanInvite(inviteCode);

        if (request.getMaxUses() != null) {
            int used = invitation.getUsedCount() != null ? invitation.getUsedCount() : 0;
            if (request.getMaxUses() < used) {
                throw new BadRequestException(
                        "Số lượt sử dụng tối đa không được nhỏ hơn số lượt đã dùng hiện tại (" + used + ")");
            }
            invitation.setMaxUses(request.getMaxUses());
        }
        if (request.getExpiresAt() != null) {
            invitation.setExpiresAt(request.getExpiresAt());
        } else if (request.getValidityDays() != null) {
            invitation.setExpiresAt(Instant.now().plus(request.getValidityDays(), ChronoUnit.DAYS));
        }
        if (request.getIsActive() != null) {
            invitation.setIsActive(request.getIsActive());
        }

        InvitationCode saved = invitationCodeRepository.save(invitation);
        log.info("Đã cập nhật mã mời: code={}, clanId={}, isActive={}, maxUses={}, expiresAt={}, bởi accountId={}",
                saved.getInviteCode(), saved.getClanId(), saved.getIsActive(), saved.getMaxUses(),
                saved.getExpiresAt(), currentAccountId);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public InviteCodeResponse regenerateInviteCode(String inviteCode, UpdateInvitationRequest request,
                                                   String currentAccountId) {
        InvitationCode current = loadClanInvite(inviteCode);

        if (current.getInviteType() == InviteType.PositionLinkCode) {
            throw new BadRequestException(
                    "Không thể tái tạo mã liên kết vị trí (target_member_id là UNIQUE); "
                            + "hãy tạo hồ sơ ảo mới để cấp mã liên kết khác");
        }

        int maxUses = request != null && request.getMaxUses() != null
                ? request.getMaxUses()
                : (current.getMaxUses() != null ? current.getMaxUses() : DEFAULT_MAX_USES);
        int used = current.getUsedCount() != null ? current.getUsedCount() : 0;
        if (maxUses < used) {
            throw new BadRequestException(
                    "Số lượt sử dụng tối đa không được nhỏ hơn số lượt đã dùng hiện tại (" + used + ")");
        }
        Instant expiresAt = resolveRegeneratedExpiry(current, request);

        String newInviteCode = generateUniqueInviteCode();

        // 1. Thu hồi mã cũ (giữ bản ghi để bảo toàn audit trail)
        current.setIsActive(false);
        invitationCodeRepository.save(current);

        // 2. Mã mới kế thừa loại mã, phạm vi nhánh và hạn mức; used_count được đặt lại
        InvitationCode replacement = InvitationCode.builder()
                .inviteCode(newInviteCode)
                .clanId(current.getClanId())
                .inviteType(current.getInviteType())
                .shareableUrl(inviteCodeGenerator.buildShareableUrl(newInviteCode))
                .targetBranchId(current.getTargetBranchId())
                .targetMemberId(null)
                .maxUses(maxUses)
                .usedCount(0)
                .expiresAt(expiresAt)
                .isActive(true)
                .createdByAccountId(currentAccountId != null && !currentAccountId.isBlank()
                        ? currentAccountId
                        : current.getCreatedByAccountId())
                .build();

        InvitationCode saved = invitationCodeRepository.save(replacement);
        log.info("Đã tái tạo mã mời: cũ={}, mới={}, clanId={}, maxUses={}, expiresAt={}, bởi accountId={}",
                current.getInviteCode(), newInviteCode, saved.getClanId(), maxUses, expiresAt, currentAccountId);

        return toResponse(saved);
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
        InvitationCode invitation = InviteCodeGuard.requireUsableForOnboarding(invitationCodeRepository, inviteCode);
        return toResponse(invitation);
    }

    @Override
    public OnboardingBranchTreeNode getOnboardingBranchTree(String inviteCode) {
        InvitationCode invitation = InviteCodeGuard.requireUsableForOnboarding(invitationCodeRepository, inviteCode);
        return genealogyQueryService.getOnboardingBranchTree(invitation.getClanId(), invitation.getTargetBranchId());
    }

    /**
     * Looks the code up inside the caller's clan so a code belonging to another clan is indistinguishable from a
     * missing one (IDOR guard).
     */
    private InvitationCode loadClanInvite(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BadRequestException("Mã mời không được để trống");
        }
        String clanId = TenantContext.getClanId();
        String trimmed = inviteCode.trim();
        return invitationCodeRepository.findByClanIdAndInviteCode(clanId, trimmed)
                .orElseThrow(() -> new ResourceNotFoundException("Mã mời", "inviteCode", trimmed));
    }

    /**
     * Expiry of the replacement code: caller override first, then the remaining validity of the old code, then the
     * default window — so regenerating never yields a code that is already expired.
     */
    private Instant resolveRegeneratedExpiry(InvitationCode current, UpdateInvitationRequest request) {
        if (request != null && request.getExpiresAt() != null) {
            return request.getExpiresAt();
        }
        if (request != null && request.getValidityDays() != null) {
            return Instant.now().plus(request.getValidityDays(), ChronoUnit.DAYS);
        }
        if (current.getExpiresAt() != null && current.getExpiresAt().isAfter(Instant.now())) {
            return current.getExpiresAt();
        }
        return Instant.now().plus(DEFAULT_VALIDITY_DAYS, ChronoUnit.DAYS);
    }

    private InviteCodeResponse toResponse(InvitationCode entity) {
        InviteCodeResponse response = InviteCodeResponse.fromEntity(entity);
        if (entity.getClanId() != null) {
            genealogyQueryService.getClanName(entity.getClanId()).ifPresent(response::setClanName);
        }
        if (entity.getClanId() != null && entity.getTargetBranchId() != null) {
            genealogyQueryService.getBranchName(entity.getClanId(), entity.getTargetBranchId())
                    .ifPresent(response::setTargetBranchName);
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