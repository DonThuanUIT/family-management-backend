package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.request.ReviewLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.ImmutableLedgerEntryResponse;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.dto.response.ReviewLedgerAdjustmentResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import com.familyfund.finance.internal.util.ImmutableLedgerIdGenerator;
import com.familyfund.finance.internal.util.LedgerAdjustmentRequestIdGenerator;
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
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service implementation for creating, reviewing and querying ledger adjustment requests (US-15 / US-59).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerAdjustmentServiceImpl implements LedgerAdjustmentService {

    private final LedgerAdjustmentRequestRepository ledgerAdjustmentRequestRepository;
    private final ImmutableLedgerRepository immutableLedgerRepository;
    private final LedgerAdjustmentRequestIdGenerator requestIdGenerator;
    private final ImmutableLedgerIdGenerator ledgerIdGenerator;
    private final ImmutableLedgerHashCalculator ledgerHashCalculator;
    private final EventQueryService eventQueryService;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerAdjustmentRequestResponse proposeAdjustment(ProposeLedgerAdjustmentRequest request) {
        if (request.proposedAdjustmentAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessRuleException("Số tiền đề xuất bù trừ phải khác 0 (có thể là số dương hoặc số âm)");
        }

        String clanId = normalizeBlank(TenantContext.getClanId());
        String txId = request.originalTransactionId().trim();

        ImmutableLedger originalTx = immutableLedgerRepository.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Giao dịch sổ cái gốc", "originalTransactionId", txId));

        if (clanId != null && !clanId.equals(originalTx.getClanId())) {
            throw new ResourceNotFoundException("Giao dịch sổ cái gốc", "originalTransactionId", txId);
        }

        validateProposerAuthorization(originalTx);

        if (ledgerAdjustmentRequestRepository.existsByOriginalTransactionIdAndApprovalStatus(
                txId, LedgerAdjustmentStatus.Pending)) {
            throw new BusinessRuleException(
                    "Giao dịch sổ cái gốc " + txId + " đang có một đề xuất bù trừ chờ phê duyệt (Pending). "
                            + "Vui lòng chờ Trưởng tộc xử lý trước khi tạo đề xuất mới.");
        }

        String proposerAccountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        String evidenceUrl = normalizeBlank(request.evidenceAttachmentUrl());
        String requestId = generateUniqueId(
                requestIdGenerator::generate,
                ledgerAdjustmentRequestRepository::existsById,
                "đề xuất điều chỉnh sổ cái"
        );

        LedgerAdjustmentRequest entity = LedgerAdjustmentRequest.builder()
                .requestId(requestId)
                .originalTransactionId(txId)
                .proposedAdjustmentAmount(request.proposedAdjustmentAmount())
                .adjustmentReason(request.adjustmentReason().trim())
                .evidenceAttachmentUrl(evidenceUrl)
                .proposedByAccountId(proposerAccountId)
                .approvalStatus(LedgerAdjustmentStatus.Pending)
                .createdAt(Instant.now())
                .build();

        LedgerAdjustmentRequest saved = ledgerAdjustmentRequestRepository.save(entity);
        log.info("US-15: Đề xuất bù trừ sổ cái {} cho giao dịch gốc {} (số tiền: {}, người lập: {})",
                saved.getRequestId(), txId, saved.getProposedAdjustmentAmount(), proposerAccountId);

        notifyClanHeadsOnProposed(saved, originalTx);

        return toResponse(saved, originalTx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewLedgerAdjustmentResponse reviewAdjustment(ReviewLedgerAdjustmentRequest request) {
        validateReviewerAuthorization();

        String rejectionReason = normalizeBlank(request.rejectionReason());
        if (!Boolean.TRUE.equals(request.approved()) && rejectionReason == null) {
            throw new BusinessRuleException(
                    "Lý do từ chối không được để trống khi từ chối đề xuất điều chỉnh sổ cái");
        }

        String clanId = normalizeBlank(TenantContext.getClanId());
        String requestId = request.requestId().trim();

        LedgerAdjustmentRequest entity = ledgerAdjustmentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Đề xuất điều chỉnh sổ cái", "requestId", requestId));

        ImmutableLedger originalTx = immutableLedgerRepository.findById(entity.getOriginalTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Giao dịch sổ cái gốc", "originalTransactionId", entity.getOriginalTransactionId()));

        if (clanId != null && !clanId.equals(originalTx.getClanId())) {
            throw new ResourceNotFoundException("Đề xuất điều chỉnh sổ cái", "requestId", requestId);
        }

        if (entity.getApprovalStatus() != LedgerAdjustmentStatus.Pending) {
            throw new BusinessRuleException(
                    "Đề xuất điều chỉnh sổ cái " + requestId + " đã được xử lý trước đó (trạng thái hiện tại: "
                            + entity.getApprovalStatus() + ")");
        }

        String reviewerAccountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        if (Boolean.TRUE.equals(request.approved())) {
            return handleApproval(entity, originalTx, reviewerAccountId, request);
        }
        return handleRejection(entity, originalTx, reviewerAccountId, rejectionReason);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerAdjustmentRequestResponse getAdjustmentRequestById(String requestId) {
        String clanId = normalizeBlank(TenantContext.getClanId());
        String normalizedRequestId = requestId != null ? requestId.trim() : "";

        LedgerAdjustmentRequest entity = ledgerAdjustmentRequestRepository.findById(normalizedRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Đề xuất điều chỉnh sổ cái", "requestId", requestId));

        ImmutableLedger originalTx = immutableLedgerRepository.findById(entity.getOriginalTransactionId())
                .orElse(null);

        if (clanId != null && (originalTx == null || !clanId.equals(originalTx.getClanId()))) {
            throw new ResourceNotFoundException("Đề xuất điều chỉnh sổ cái", "requestId", requestId);
        }

        return toResponse(entity, originalTx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAdjustmentRequestResponse> searchAdjustmentRequests(
            String originalTransactionId,
            LedgerAdjustmentStatus status) {
        String clanId = normalizeBlank(TenantContext.getClanId());
        String effectiveTxId = normalizeBlank(originalTransactionId);

        List<LedgerAdjustmentRequest> requests =
                ledgerAdjustmentRequestRepository.findByFilter(clanId, effectiveTxId, status);

        if (requests.isEmpty()) {
            return List.of();
        }

        Set<String> txIds = requests.stream()
                .map(LedgerAdjustmentRequest::getOriginalTransactionId)
                .collect(Collectors.toSet());

        Map<String, ImmutableLedger> ledgerMap = immutableLedgerRepository.findAllById(txIds).stream()
                .collect(Collectors.toMap(ImmutableLedger::getTransactionId, Function.identity()));

        return requests.stream()
                .map(req -> toResponse(req, ledgerMap.get(req.getOriginalTransactionId())))
                .toList();
    }

    private void validateProposerAuthorization(ImmutableLedger originalTx) {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)
                || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return;
        }

        Optional<String> memberIdOpt = currentUserProvider.currentMemberId();
        if (memberIdOpt.isPresent() && originalTx.getEventId() != null) {
            Set<EventRole> eventRoles =
                    eventQueryService.getMemberRolesInEvent(originalTx.getEventId(), memberIdOpt.get());
            if (eventRoles.contains(EventRole.EventTreasurer)
                    || eventRoles.contains(EventRole.HeadOfOrganizer)) {
                return;
            }
        }

        throw new AccessDeniedException(
                "Chỉ Thủ quỹ sự kiện, Trưởng ban tổ chức hoặc Trưởng tộc mới có quyền đề xuất điều chỉnh bút toán sổ cái");
    }

    private void validateReviewerAuthorization() {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)
                || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return;
        }
        throw new AccessDeniedException(
                "Chỉ Trưởng tộc hoặc Quản trị viên mới có quyền phê duyệt/từ chối đề xuất điều chỉnh sổ cái");
    }

    private ReviewLedgerAdjustmentResponse handleApproval(
            LedgerAdjustmentRequest entity,
            ImmutableLedger originalTx,
            String reviewerAccountId,
            ReviewLedgerAdjustmentRequest request) {
        immutableLedgerRepository.acquireClanLedgerLock(originalTx.getClanId());

        Optional<ImmutableLedger> latestOpt =
                immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(
                        originalTx.getClanId());

        BigDecimal currentBalance = latestOpt.map(ImmutableLedger::getRunningBalance).orElse(BigDecimal.ZERO);
        BigDecimal adjustmentAmount = entity.getProposedAdjustmentAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal newRunningBalance = currentBalance.add(adjustmentAmount).setScale(2, RoundingMode.HALF_UP);

        if (newRunningBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(
                    "Không thể phê duyệt đề xuất " + entity.getRequestId()
                            + ": số dư Quỹ chung sau bù trừ sẽ bị âm ("
                            + newRunningBalance.toPlainString() + "đ)");
        }

        String previousHash = latestOpt.map(ImmutableLedger::getRecordHash)
                .orElse(ImmutableLedgerHashCalculator.GENESIS_HASH);
        Instant entryTimestamp = resolveMonotonicTimestamp(latestOpt.orElse(null));
        String transactionId = generateUniqueId(
                ledgerIdGenerator::generate,
                immutableLedgerRepository::existsById,
                "giao dịch sổ cái"
        );

        String customDesc = normalizeBlank(request.description());
        String description = customDesc != null
                ? customDesc
                : "Bù trừ giao dịch " + originalTx.getTransactionId()
                        + " (" + entity.getRequestId() + "): " + entity.getAdjustmentReason();

        String recordHash = ledgerHashCalculator.computeRecordHash(
                previousHash,
                transactionId,
                originalTx.getClanId(),
                originalTx.getEventId(),
                entity.getRequestId(),
                originalTx.getTransactionId(),
                LedgerTransactionType.AdjustmentTransaction,
                adjustmentAmount,
                newRunningBalance,
                description,
                entity.getProposedByAccountId(),
                reviewerAccountId,
                entryTimestamp
        );

        ImmutableLedger newLedger = ImmutableLedger.builder()
                .transactionId(transactionId)
                .clanId(originalTx.getClanId())
                .eventId(originalTx.getEventId())
                .adjustmentRequestId(entity.getRequestId())
                .originalTransactionId(originalTx.getTransactionId())
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(adjustmentAmount)
                .runningBalance(newRunningBalance)
                .description(description)
                .createdByAccountId(entity.getProposedByAccountId())
                .approvedByAccountId(reviewerAccountId)
                .previousHash(previousHash)
                .recordHash(recordHash)
                .createdAt(entryTimestamp)
                .build();

        ImmutableLedger savedLedger = immutableLedgerRepository.saveAndFlush(newLedger);

        entity.setApprovalStatus(LedgerAdjustmentStatus.Approved);
        entity.setReviewedByAccountId(reviewerAccountId);
        entity.setRejectionReason(null);
        entity.setReviewedAt(entryTimestamp);
        LedgerAdjustmentRequest savedRequest = ledgerAdjustmentRequestRepository.save(entity);

        log.info("US-59: Đã duyệt đề xuất {} và ghi bút toán bù trừ {} (số dư mới: {}, hash: {}...)",
                savedRequest.getRequestId(), savedLedger.getTransactionId(), newRunningBalance,
                recordHash.substring(0, 12));

        notifyProposerOnReviewed(savedRequest, originalTx, savedLedger);

        return new ReviewLedgerAdjustmentResponse(
                toResponse(savedRequest, originalTx),
                toLedgerResponse(savedLedger)
        );
    }

    private ReviewLedgerAdjustmentResponse handleRejection(
            LedgerAdjustmentRequest entity,
            ImmutableLedger originalTx,
            String reviewerAccountId,
            String rejectionReason) {
        Instant reviewedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        entity.setApprovalStatus(LedgerAdjustmentStatus.Rejected);
        entity.setReviewedByAccountId(reviewerAccountId);
        entity.setRejectionReason(rejectionReason);
        entity.setReviewedAt(reviewedAt);

        LedgerAdjustmentRequest savedRequest = ledgerAdjustmentRequestRepository.save(entity);
        log.info("US-59: Đã từ chối đề xuất {} bởi tài khoản {}", savedRequest.getRequestId(), reviewerAccountId);

        notifyProposerOnReviewed(savedRequest, originalTx, null);

        return new ReviewLedgerAdjustmentResponse(toResponse(savedRequest, originalTx), null);
    }

    private void notifyClanHeadsOnProposed(LedgerAdjustmentRequest saved, ImmutableLedger originalTx) {
        try {
            List<AccountSummaryDto> activeAccounts =
                    identityRoleCommandService.findActiveAccountsByClan(originalTx.getClanId());
            if (activeAccounts == null || activeAccounts.isEmpty()) {
                return;
            }

            String formattedSignAmount = formatSignedAmount(saved.getProposedAdjustmentAmount());

            for (AccountSummaryDto acc : activeAccounts) {
                boolean isClanHead = (acc.roles() != null && acc.roles().contains(SystemRoles.CLAN_HEAD))
                        || identityRoleCommandService.hasRole(acc.accountId(), SystemRoles.CLAN_HEAD);
                if (!isClanHead) {
                    continue;
                }
                notificationService.notify(NotificationCommand.builder()
                        .clanId(originalTx.getClanId())
                        .recipientAccountId(acc.accountId())
                        .category(NotificationCategory.Finance)
                        .title("Đề xuất điều chỉnh sổ cái mới: " + saved.getRequestId())
                        .content("Đề xuất bù trừ " + formattedSignAmount + "đ cho giao dịch gốc "
                                + saved.getOriginalTransactionId() + ". Lý do: " + saved.getAdjustmentReason())
                        .referenceType("LEDGER_ADJUSTMENT_REQUEST")
                        .referenceId(saved.getRequestId())
                        .build());
            }
        } catch (RuntimeException ex) {
            log.warn("US-15: Không thể gửi thông báo đề xuất điều chỉnh sổ cái {}: {}",
                    saved.getRequestId(), ex.getMessage());
        }
    }

    private void notifyProposerOnReviewed(
            LedgerAdjustmentRequest saved,
            ImmutableLedger originalTx,
            ImmutableLedger newLedger) {
        String recipientId = saved.getProposedByAccountId();
        if (recipientId == null || recipientId.isBlank() || "SYSTEM".equals(recipientId)) {
            return;
        }
        try {
            boolean approved = newLedger != null;
            String title = approved
                    ? "Đề xuất điều chỉnh sổ cái đã được duyệt: " + saved.getRequestId()
                    : "Đề xuất điều chỉnh sổ cái bị từ chối: " + saved.getRequestId();
            String content = approved
                    ? "Đề xuất " + saved.getRequestId() + " đã được phê duyệt và ghi bút toán "
                            + newLedger.getTransactionId() + " (" + formatSignedAmount(newLedger.getAmount())
                            + "đ). Số dư Quỹ chung mới: " + newLedger.getRunningBalance().toPlainString() + "đ."
                    : "Đề xuất " + saved.getRequestId() + " cho giao dịch " + saved.getOriginalTransactionId()
                            + " đã bị từ chối. Lý do: " + saved.getRejectionReason();

            notificationService.notify(NotificationCommand.builder()
                    .clanId(originalTx.getClanId())
                    .recipientAccountId(recipientId)
                    .category(NotificationCategory.Finance)
                    .title(title)
                    .content(content)
                    .referenceType("LEDGER_ADJUSTMENT_REQUEST")
                    .referenceId(saved.getRequestId())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("US-59: Không thể gửi thông báo kết quả duyệt đề xuất {}: {}",
                    saved.getRequestId(), ex.getMessage());
        }
    }

    private LedgerAdjustmentRequestResponse toResponse(LedgerAdjustmentRequest entity, ImmutableLedger originalTx) {
        return new LedgerAdjustmentRequestResponse(
                entity.getRequestId(),
                entity.getOriginalTransactionId(),
                originalTx != null ? originalTx.getClanId() : null,
                originalTx != null ? originalTx.getEventId() : null,
                originalTx != null ? originalTx.getTransactionType() : null,
                originalTx != null ? originalTx.getAmount() : null,
                entity.getProposedAdjustmentAmount(),
                entity.getAdjustmentReason(),
                entity.getEvidenceAttachmentUrl(),
                entity.getProposedByAccountId(),
                entity.getApprovalStatus(),
                entity.getReviewedByAccountId(),
                entity.getRejectionReason(),
                entity.getCreatedAt(),
                entity.getReviewedAt()
        );
    }

    private ImmutableLedgerEntryResponse toLedgerResponse(ImmutableLedger entry) {
        return ImmutableLedgerEntryResponse.fromEntity(entry);
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

    private static String formatSignedAmount(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) > 0
                ? "+" + amount.toPlainString()
                : amount.toPlainString();
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
