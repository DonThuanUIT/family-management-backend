package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service implementation for creating and querying ledger adjustment requests (US-15).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerAdjustmentServiceImpl implements LedgerAdjustmentService {

    private final LedgerAdjustmentRequestRepository ledgerAdjustmentRequestRepository;
    private final ImmutableLedgerRepository immutableLedgerRepository;
    private final LedgerAdjustmentRequestIdGenerator requestIdGenerator;
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
        String requestId = generateUniqueRequestId();

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

    private void notifyClanHeadsOnProposed(LedgerAdjustmentRequest saved, ImmutableLedger originalTx) {
        try {
            List<AccountSummaryDto> activeAccounts =
                    identityRoleCommandService.findActiveAccountsByClan(originalTx.getClanId());
            if (activeAccounts == null || activeAccounts.isEmpty()) {
                return;
            }

            String formattedSignAmount = saved.getProposedAdjustmentAmount().compareTo(BigDecimal.ZERO) > 0
                    ? "+" + saved.getProposedAdjustmentAmount().toPlainString()
                    : saved.getProposedAdjustmentAmount().toPlainString();

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

    private String generateUniqueRequestId() {
        for (int i = 0; i < 5; i++) {
            String candidate = requestIdGenerator.generate();
            if (!ledgerAdjustmentRequestRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không thể sinh mã đề xuất điều chỉnh sổ cái duy nhất sau 5 lần thử");
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
