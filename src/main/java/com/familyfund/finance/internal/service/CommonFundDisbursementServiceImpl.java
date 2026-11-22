package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.request.ProposeCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.request.ReviewCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.response.CommonFundBalanceResponse;
import com.familyfund.finance.internal.dto.response.CommonFundDisbursementResponse;
import com.familyfund.finance.internal.dto.response.ImmutableLedgerEntryResponse;
import com.familyfund.finance.internal.dto.response.ReviewCommonFundDisbursementResponse;
import com.familyfund.finance.internal.entity.CommonFundDisbursementRequest;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.repository.CommonFundDisbursementRequestRepository;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.util.CommonFundDisbursementIdGenerator;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import com.familyfund.finance.internal.util.ImmutableLedgerIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.AccountPasswordVerificationService;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service implementation for common fund balance queries, disbursement proposals,
 * and Clan Head approvals with second-factor password confirmation (US-60).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonFundDisbursementServiceImpl implements CommonFundDisbursementService {

    private final CommonFundDisbursementRequestRepository disbursementRepository;
    private final ImmutableLedgerRepository immutableLedgerRepository;
    private final CommonFundDisbursementIdGenerator disbursementIdGenerator;
    private final ImmutableLedgerIdGenerator ledgerIdGenerator;
    private final ImmutableLedgerHashCalculator ledgerHashCalculator;
    private final EventQueryService eventQueryService;
    private final GenealogyQueryService genealogyQueryService;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final AccountPasswordVerificationService accountPasswordVerificationService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public CommonFundBalanceResponse getFundBalance() {
        String clanId = requireClanContext("kiểm tra số dư Quỹ chung");
        Optional<ImmutableLedger> latestOpt =
                immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(clanId);

        BigDecimal availableBalance = latestOpt
                .map(ImmutableLedger::getRunningBalance)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pendingAmount = Optional.ofNullable(disbursementRepository.sumPendingAmountByClanId(clanId))
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new CommonFundBalanceResponse(
                clanId,
                availableBalance,
                pendingAmount,
                latestOpt.map(ImmutableLedger::getTransactionId).orElse(null),
                latestOpt.map(ImmutableLedger::getCreatedAt).orElse(null)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonFundDisbursementResponse proposeDisbursement(ProposeCommonFundDisbursementRequest request) {
        String clanId = requireClanContext("lập đề xuất chi Quỹ chung");
        validateProposerAuthorization(clanId);

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Số tiền đề xuất chi Quỹ chung phải lớn hơn 0");
        }

        String beneficiaryMemberId = normalizeBlank(request.beneficiaryMemberId());
        String beneficiaryName = resolveBeneficiaryName(clanId, beneficiaryMemberId, request.beneficiaryName());

        BigDecimal availableBalance = immutableLedgerRepository
                .findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(clanId)
                .map(ImmutableLedger::getRunningBalance)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        if (availableBalance.compareTo(amount) < 0) {
            throw new BusinessRuleException(
                    "Số dư khả dụng Quỹ chung (" + availableBalance.toPlainString()
                            + "đ) không đủ để đề xuất chi " + amount.toPlainString() + "đ");
        }

        String proposerAccountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        String requestId = generateUniqueId(
                disbursementIdGenerator::generate,
                disbursementRepository::existsById,
                "phiếu đề xuất chi Quỹ chung"
        );

        CommonFundDisbursementRequest entity = CommonFundDisbursementRequest.builder()
                .requestId(requestId)
                .clanId(clanId)
                .title(request.title().trim())
                .category(request.category())
                .amount(amount)
                .beneficiaryName(beneficiaryName)
                .beneficiaryMemberId(beneficiaryMemberId)
                .purpose(request.purpose().trim())
                .attachmentUrl(normalizeBlank(request.attachmentUrl()))
                .proposedByAccountId(proposerAccountId)
                .approvalStatus(CommonFundDisbursementStatus.Pending)
                .createdAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .build();

        CommonFundDisbursementRequest saved = disbursementRepository.save(entity);
        log.info("US-60: Tạo đề xuất chi Quỹ chung {} (danh mục: {}, số tiền: {}, người lập: {})",
                saved.getRequestId(), saved.getCategory(), saved.getAmount(), proposerAccountId);

        notifyClanHeadsOnProposed(saved);

        return toResponse(saved, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCommonFundDisbursementResponse reviewDisbursement(
            String pathRequestId,
            ReviewCommonFundDisbursementRequest request) {
        validateReviewerAuthorization();

        String requestId = resolveEffectiveRequestId(pathRequestId, request.requestId());
        String reviewerAccountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        boolean approved = Boolean.TRUE.equals(request.approved());

        String rejectionReason = normalizeBlank(request.rejectionReason());
        if (!approved && rejectionReason == null) {
            throw new BusinessRuleException(
                    "Lý do từ chối không được để trống khi từ chối đề xuất chi Quỹ chung");
        }

        if (approved) {
            if (request.confirmPassword() == null || request.confirmPassword().isBlank()) {
                throw new BusinessRuleException(
                        "Mật khẩu xác nhận không được để trống khi phê duyệt chi Quỹ chung");
            }
            boolean passwordMatched = accountPasswordVerificationService.verifyActiveAccountPassword(
                    reviewerAccountId, request.confirmPassword());
            if (!passwordMatched) {
                throw new BusinessRuleException(
                        "Mật khẩu xác nhận không chính xác hoặc tài khoản không ở trạng thái hoạt động");
            }
        }

        String clanId = normalizeBlank(TenantContext.getClanId());
        CommonFundDisbursementRequest entity = disbursementRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Đề xuất chi Quỹ chung", "requestId", requestId));

        if (clanId != null && !clanId.equals(entity.getClanId())) {
            throw new ResourceNotFoundException("Đề xuất chi Quỹ chung", "requestId", requestId);
        }

        if (entity.getApprovalStatus() != CommonFundDisbursementStatus.Pending) {
            throw new BusinessRuleException(
                    "Đề xuất chi Quỹ chung " + requestId + " đã được xử lý trước đó (trạng thái hiện tại: "
                            + entity.getApprovalStatus() + ")");
        }

        if (approved) {
            return handleApproval(entity, reviewerAccountId, request);
        }
        return handleRejection(entity, reviewerAccountId, rejectionReason);
    }

    @Override
    @Transactional(readOnly = true)
    public CommonFundDisbursementResponse getDisbursementById(String requestId) {
        String clanId = normalizeBlank(TenantContext.getClanId());
        String normalizedId = requestId != null ? requestId.trim() : "";

        CommonFundDisbursementRequest entity = disbursementRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Đề xuất chi Quỹ chung", "requestId", requestId));

        if (clanId != null && !clanId.equals(entity.getClanId())) {
            throw new ResourceNotFoundException("Đề xuất chi Quỹ chung", "requestId", requestId);
        }

        String ledgerTxId = entity.getApprovalStatus() == CommonFundDisbursementStatus.Approved
                ? immutableLedgerRepository.findByDisbursementRequestId(entity.getRequestId())
                        .map(ImmutableLedger::getTransactionId)
                        .orElse(null)
                : null;

        return toResponse(entity, ledgerTxId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommonFundDisbursementResponse> searchDisbursements(
            CommonFundDisbursementStatus status,
            CommonFundExpenseCategory category) {
        String clanId = normalizeBlank(TenantContext.getClanId());
        List<CommonFundDisbursementRequest> requests =
                disbursementRepository.findByFilter(clanId, status, category);

        if (requests.isEmpty()) {
            return List.of();
        }

        List<String> approvedRequestIds = requests.stream()
                .filter(r -> r.getApprovalStatus() == CommonFundDisbursementStatus.Approved)
                .map(CommonFundDisbursementRequest::getRequestId)
                .toList();

        Map<String, String> ledgerTxIdMap = approvedRequestIds.isEmpty()
                ? Map.of()
                : immutableLedgerRepository.findByDisbursementRequestIdIn(approvedRequestIds).stream()
                        .collect(Collectors.toMap(
                                ImmutableLedger::getDisbursementRequestId,
                                ImmutableLedger::getTransactionId,
                                (first, second) -> first
                        ));

        return requests.stream()
                .map(req -> toResponse(req, ledgerTxIdMap.get(req.getRequestId())))
                .toList();
    }

    private ReviewCommonFundDisbursementResponse handleApproval(
            CommonFundDisbursementRequest entity,
            String reviewerAccountId,
            ReviewCommonFundDisbursementRequest request) {
        immutableLedgerRepository.acquireClanLedgerLock(entity.getClanId());

        Optional<ImmutableLedger> latestOpt =
                immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(entity.getClanId());

        BigDecimal currentBalance = latestOpt
                .map(ImmutableLedger::getRunningBalance)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal disbursementAmount = entity.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal newRunningBalance = currentBalance.subtract(disbursementAmount).setScale(2, RoundingMode.HALF_UP);

        if (newRunningBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(
                    "Không thể phê duyệt đề xuất " + entity.getRequestId()
                            + ": số dư khả dụng Quỹ chung (" + currentBalance.toPlainString()
                            + "đ) không đủ để chi " + disbursementAmount.toPlainString() + "đ");
        }

        String previousHash = latestOpt
                .map(ImmutableLedger::getRecordHash)
                .orElse(ImmutableLedgerHashCalculator.GENESIS_HASH);
        Instant entryTimestamp = resolveMonotonicTimestamp(latestOpt.orElse(null));
        String transactionId = generateUniqueId(
                ledgerIdGenerator::generate,
                immutableLedgerRepository::existsById,
                "giao dịch sổ cái"
        );

        BigDecimal ledgerAmount = disbursementAmount.negate().setScale(2, RoundingMode.HALF_UP);
        String customDesc = normalizeBlank(request.description());
        String description = customDesc != null
                ? customDesc
                : "Chi Quỹ chung (" + entity.getRequestId() + " - " + entity.getCategory().name()
                        + "): " + entity.getTitle() + " - " + entity.getPurpose();

        String recordHash = ledgerHashCalculator.computeRecordHash(
                previousHash,
                transactionId,
                entity.getClanId(),
                null,
                null,
                entity.getRequestId(),
                null,
                LedgerTransactionType.CommonFundDisbursement,
                ledgerAmount,
                newRunningBalance,
                description,
                entity.getProposedByAccountId(),
                reviewerAccountId,
                entryTimestamp
        );

        ImmutableLedger newLedger = ImmutableLedger.builder()
                .transactionId(transactionId)
                .clanId(entity.getClanId())
                .disbursementRequestId(entity.getRequestId())
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(ledgerAmount)
                .runningBalance(newRunningBalance)
                .description(description)
                .createdByAccountId(entity.getProposedByAccountId())
                .approvedByAccountId(reviewerAccountId)
                .recordHash(recordHash)
                .createdAt(entryTimestamp)
                .build();

        ImmutableLedger savedLedger = immutableLedgerRepository.saveAndFlush(newLedger);

        entity.setApprovalStatus(CommonFundDisbursementStatus.Approved);
        entity.setReviewedByAccountId(reviewerAccountId);
        entity.setRejectionReason(null);
        entity.setReviewedAt(entryTimestamp);
        CommonFundDisbursementRequest savedRequest = disbursementRepository.save(entity);

        log.info("US-60: Đã duyệt chi Quỹ chung {} và ghi bút toán {} (-{}đ, số dư mới: {}đ, hash: {}...)",
                savedRequest.getRequestId(), savedLedger.getTransactionId(),
                disbursementAmount.toPlainString(), newRunningBalance.toPlainString(),
                recordHash.substring(0, 12));

        notifyProposerOnReviewed(savedRequest, savedLedger);

        return new ReviewCommonFundDisbursementResponse(
                toResponse(savedRequest, savedLedger.getTransactionId()),
                ImmutableLedgerEntryResponse.fromEntity(savedLedger)
        );
    }

    private ReviewCommonFundDisbursementResponse handleRejection(
            CommonFundDisbursementRequest entity,
            String reviewerAccountId,
            String rejectionReason) {
        Instant reviewedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        entity.setApprovalStatus(CommonFundDisbursementStatus.Rejected);
        entity.setReviewedByAccountId(reviewerAccountId);
        entity.setRejectionReason(rejectionReason);
        entity.setReviewedAt(reviewedAt);

        CommonFundDisbursementRequest savedRequest = disbursementRepository.save(entity);
        log.info("US-60: Đã từ chối đề xuất chi Quỹ chung {} bởi tài khoản {}",
                savedRequest.getRequestId(), reviewerAccountId);

        notifyProposerOnReviewed(savedRequest, null);

        return new ReviewCommonFundDisbursementResponse(toResponse(savedRequest, null), null);
    }

    private void validateProposerAuthorization(String clanId) {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)
                || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)
                || currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)) {
            return;
        }
        Optional<String> memberIdOpt = currentUserProvider.currentMemberId();
        if (memberIdOpt.isPresent()
                && eventQueryService.hasEventRoleInClan(clanId, memberIdOpt.get(), EventRole.EventTreasurer)) {
            return;
        }
        throw new AccessDeniedException(
                "Chỉ Trưởng tộc, Trưởng chi, Thủ quỹ sự kiện hoặc Quản trị viên mới có quyền lập đề xuất chi Quỹ chung");
    }

    private void validateReviewerAuthorization() {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)
                || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return;
        }
        throw new AccessDeniedException(
                "Chỉ Trưởng tộc hoặc Quản trị viên mới có quyền phê duyệt/từ chối đề xuất chi Quỹ chung");
    }

    private String resolveBeneficiaryName(String clanId, String beneficiaryMemberId, String explicitName) {
        String normalizedExplicit = normalizeBlank(explicitName);
        if (beneficiaryMemberId == null) {
            return normalizedExplicit;
        }
        MemberSummaryDto member = genealogyQueryService.findMemberSummary(beneficiaryMemberId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Thành viên thụ hưởng", "beneficiaryMemberId", beneficiaryMemberId));
        if (member.clanId() != null && !clanId.equals(member.clanId())) {
            throw new ResourceNotFoundException(
                    "Thành viên thụ hưởng", "beneficiaryMemberId", beneficiaryMemberId);
        }
        return normalizedExplicit != null ? normalizedExplicit : member.fullName();
    }

    private String resolveEffectiveRequestId(String pathRequestId, String bodyRequestId) {
        String pathId = normalizeBlank(pathRequestId);
        String bodyId = normalizeBlank(bodyRequestId);
        if (pathId != null && bodyId != null && !pathId.equals(bodyId)) {
            throw new BusinessRuleException(
                    "Mã đề xuất trong đường dẫn (" + pathId + ") không khớp với requestId trong payload (" + bodyId + ")");
        }
        String effective = pathId != null ? pathId : bodyId;
        if (effective == null) {
            throw new BusinessRuleException("Mã đề xuất chi Quỹ chung không được để trống");
        }
        return effective;
    }

    private void notifyClanHeadsOnProposed(CommonFundDisbursementRequest saved) {
        try {
            List<AccountSummaryDto> activeAccounts =
                    identityRoleCommandService.findActiveAccountsByClan(saved.getClanId());
            if (activeAccounts == null || activeAccounts.isEmpty()) {
                return;
            }
            for (AccountSummaryDto acc : activeAccounts) {
                boolean isClanHead = (acc.roles() != null && acc.roles().contains(SystemRoles.CLAN_HEAD))
                        || identityRoleCommandService.hasRole(acc.accountId(), SystemRoles.CLAN_HEAD);
                if (!isClanHead) {
                    continue;
                }
                notificationService.notify(NotificationCommand.builder()
                        .clanId(saved.getClanId())
                        .recipientAccountId(acc.accountId())
                        .category(NotificationCategory.Finance)
                        .title("Đề xuất chi Quỹ chung mới: " + saved.getTitle())
                        .content("Phiếu " + saved.getRequestId() + " (" + saved.getCategory().name()
                                + ") đề xuất chi " + saved.getAmount().toPlainString()
                                + "đ. Mục đích: " + saved.getPurpose())
                        .referenceType("COMMON_FUND_DISBURSEMENT")
                        .referenceId(saved.getRequestId())
                        .build());
            }
        } catch (RuntimeException ex) {
            log.warn("US-60: Không thể gửi thông báo đề xuất chi Quỹ chung {}: {}",
                    saved.getRequestId(), ex.getMessage());
        }
    }

    private void notifyProposerOnReviewed(CommonFundDisbursementRequest saved, ImmutableLedger newLedger) {
        String recipientId = saved.getProposedByAccountId();
        if (recipientId == null || recipientId.isBlank() || "SYSTEM".equals(recipientId)) {
            return;
        }
        try {
            boolean approved = newLedger != null;
            String title = approved
                    ? "Đề xuất chi Quỹ chung đã được duyệt: " + saved.getTitle()
                    : "Đề xuất chi Quỹ chung bị từ chối: " + saved.getTitle();
            String content = approved
                    ? "Phiếu " + saved.getRequestId() + " (-" + saved.getAmount().toPlainString()
                            + "đ) đã được phê duyệt và ghi bút toán " + newLedger.getTransactionId()
                            + ". Số dư Quỹ chung mới: " + newLedger.getRunningBalance().toPlainString() + "đ."
                    : "Phiếu " + saved.getRequestId() + " đã bị từ chối. Lý do: " + saved.getRejectionReason();

            notificationService.notify(NotificationCommand.builder()
                    .clanId(saved.getClanId())
                    .recipientAccountId(recipientId)
                    .category(NotificationCategory.Finance)
                    .title(title)
                    .content(content)
                    .referenceType("COMMON_FUND_DISBURSEMENT")
                    .referenceId(saved.getRequestId())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("US-60: Không thể gửi thông báo kết quả duyệt phiếu chi {}: {}",
                    saved.getRequestId(), ex.getMessage());
        }
    }

    private CommonFundDisbursementResponse toResponse(
            CommonFundDisbursementRequest entity,
            String ledgerTransactionId) {
        return new CommonFundDisbursementResponse(
                entity.getRequestId(),
                entity.getClanId(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getAmount(),
                entity.getBeneficiaryName(),
                entity.getBeneficiaryMemberId(),
                entity.getPurpose(),
                entity.getAttachmentUrl(),
                entity.getProposedByAccountId(),
                entity.getApprovalStatus(),
                entity.getReviewedByAccountId(),
                entity.getRejectionReason(),
                ledgerTransactionId,
                entity.getCreatedAt(),
                entity.getReviewedAt()
        );
    }

    private Instant resolveMonotonicTimestamp(ImmutableLedger latestLedger) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (latestLedger != null && latestLedger.getCreatedAt() != null) {
            Instant latestMicros = latestLedger.getCreatedAt().truncatedTo(ChronoUnit.MICROS);
            if (!now.isAfter(latestMicros)) {
                return latestMicros.plus(1, ChronoUnit.MICROS);
            }
        }
        return now;
    }

    private String generateUniqueId(
            Supplier<String> generator,
            Predicate<String> existsById,
            String entityLabel) {
        for (int i = 0; i < 5; i++) {
            String candidate = generator.get();
            if (!existsById.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không thể sinh mã " + entityLabel + " duy nhất sau 5 lần thử");
    }

    private static String requireClanContext(String actionDescription) {
        String clanId = normalizeBlank(TenantContext.getClanId());
        if (clanId == null) {
            throw new BusinessRuleException("Thiếu ngữ cảnh dòng họ (clanId) để " + actionDescription);
        }
        return clanId;
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
