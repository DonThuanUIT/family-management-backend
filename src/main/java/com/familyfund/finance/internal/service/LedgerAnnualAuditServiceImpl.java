package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.LedgerAnnualAuditStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.request.AnnualLedgerAuditRequest;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.ImmutableLedgerEntryResponse;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.dto.response.LedgerAnnualAuditResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.entity.LedgerAnnualAudit;
import com.familyfund.finance.internal.repository.CommonFundDisbursementRequestRepository;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
import com.familyfund.finance.internal.repository.LedgerAnnualAuditRepository;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import com.familyfund.finance.internal.util.ImmutableLedgerIdGenerator;
import com.familyfund.finance.internal.util.LedgerAnnualAuditIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service implementation for performing annual common-fund ledger reconciliations,
 * auto-generating US-15 adjustment requests on discrepancies, and sealing matched
 * opening balances into {@code immutable_ledgers} with SHA-256 hash chaining.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerAnnualAuditServiceImpl implements LedgerAnnualAuditService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int MIN_AUDIT_YEAR = 2000;
    private static final int MAX_AUDIT_YEAR = 2100;
    private static final int MAX_REASON_LENGTH = 2000;

    private final LedgerAnnualAuditRepository ledgerAnnualAuditRepository;
    private final ImmutableLedgerRepository immutableLedgerRepository;
    private final LedgerAdjustmentRequestRepository ledgerAdjustmentRequestRepository;
    private final CommonFundDisbursementRequestRepository commonFundDisbursementRequestRepository;
    private final LedgerAdjustmentService ledgerAdjustmentService;
    private final LedgerAnnualAuditIdGenerator auditIdGenerator;
    private final ImmutableLedgerIdGenerator ledgerIdGenerator;
    private final ImmutableLedgerHashCalculator ledgerHashCalculator;
    private final GenealogyQueryService genealogyQueryService;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerAnnualAuditResponse performAnnualAudit(AnnualLedgerAuditRequest request) {
        String clanId = requireClanId();
        validateAuditorAuthorization();
        if (genealogyQueryService.getClanName(clanId).isEmpty()) {
            throw new ResourceNotFoundException("Dòng họ", "clanId", clanId);
        }

        if (request == null || request.actualBalance() == null) {
            throw new BusinessRuleException("Số tiền thực kiểm không được để trống");
        }

        BigDecimal actualBalance = request.actualBalance().setScale(2, RoundingMode.HALF_UP);
        if (actualBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Số tiền thực kiểm không được âm");
        }

        int auditYear = request.auditYear() != null
                ? request.auditYear()
                : LocalDate.now(VIETNAM_ZONE).getYear();
        validateAuditYearRange(auditYear);
        int nextPeriodYear = auditYear + 1;

        String requestedRefTxId = normalizeBlank(request.referenceTransactionId());
        String notes = normalizeBlank(request.notes());
        String evidenceUrl = normalizeBlank(request.evidenceAttachmentUrl());

        immutableLedgerRepository.acquireClanLedgerLock(clanId);

        if (ledgerAnnualAuditRepository.existsByClanIdAndAuditYearAndAuditStatus(
                clanId, auditYear, LedgerAnnualAuditStatus.Matched)) {
            throw new DuplicateResourceException(
                    "Kỳ kiểm toán năm " + auditYear + " của dòng họ đã được chốt khớp số dư đầu kỳ trước đó");
        }

        List<ImmutableLedger> orderedEntries =
                nullSafeList(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(clanId));

        ImmutableLedgerHashCalculator.ChainVerificationResult verification =
                ledgerHashCalculator.verifyChain(orderedEntries);
        if (!verification.valid()) {
            log.error("SECURITY VIOLATION [NFR-01]: Phát hiện {} điểm gãy chuỗi SHA-256 trên Sổ cái dòng họ {} khi kiểm toán năm {}",
                    verification.brokenLinks().size(), clanId, auditYear);
            throw new LedgerImmutabilityViolationException(
                    "Vi phạm toàn vẹn sổ cái bất biến (NFR-01): Phát hiện " + verification.brokenLinks().size()
                            + " điểm gãy chuỗi mã băm SHA-256. Không thể thực hiện kiểm toán năm cho đến khi được điều tra.");
        }

        ensureNoPendingRequests(clanId);

        if (requestedRefTxId != null) {
            immutableLedgerRepository.findByTransactionIdAndClanId(requestedRefTxId, clanId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Giao dịch sổ cái đối chiếu", "referenceTransactionId", requestedRefTxId));
        }

        ImmutableLedger latestLedger = orderedEntries.isEmpty()
                ? null
                : orderedEntries.get(orderedEntries.size() - 1);
        BigDecimal systemBalance = (latestLedger != null && latestLedger.getRunningBalance() != null
                ? latestLedger.getRunningBalance()
                : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discrepancy = actualBalance.subtract(systemBalance).setScale(2, RoundingMode.HALF_UP);

        String auditorAccountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        String auditId = generateUniqueId(
                auditIdGenerator::generate,
                ledgerAnnualAuditRepository::existsById,
                "biên bản kiểm toán định kỳ sổ cái"
        );

        if (discrepancy.compareTo(BigDecimal.ZERO) == 0) {
            return handleMatchedAudit(
                    auditId,
                    clanId,
                    auditYear,
                    nextPeriodYear,
                    systemBalance,
                    actualBalance,
                    requestedRefTxId,
                    notes,
                    evidenceUrl,
                    auditorAccountId,
                    latestLedger
            );
        }

        return handleDiscrepancyAudit(
                auditId,
                clanId,
                auditYear,
                nextPeriodYear,
                systemBalance,
                actualBalance,
                discrepancy,
                requestedRefTxId,
                notes,
                evidenceUrl,
                auditorAccountId,
                latestLedger
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAnnualAuditResponse> searchAnnualAudits(Integer auditYear, LedgerAnnualAuditStatus status) {
        String clanId = requireClanId();
        if (auditYear != null) {
            validateAuditYearRange(auditYear);
        }
        List<LedgerAnnualAudit> audits =
                nullSafeList(ledgerAnnualAuditRepository.findByFilter(clanId, auditYear, status));
        return enrichAudits(clanId, audits);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerAnnualAuditResponse getAnnualAuditById(String auditId) {
        String clanId = requireClanId();
        String normalizedId = normalizeBlank(auditId);
        if (normalizedId == null) {
            throw new ResourceNotFoundException("Biên bản kiểm toán định kỳ sổ cái", "auditId", auditId);
        }

        LedgerAnnualAudit audit = ledgerAnnualAuditRepository.findByAuditIdAndClanId(normalizedId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Biên bản kiểm toán định kỳ sổ cái", "auditId", auditId));

        return enrichAudits(clanId, List.of(audit)).get(0);
    }

    private LedgerAnnualAuditResponse handleMatchedAudit(
            String auditId,
            String clanId,
            int auditYear,
            int nextPeriodYear,
            BigDecimal systemBalance,
            BigDecimal actualBalance,
            String requestedRefTxId,
            String notes,
            String evidenceUrl,
            String auditorAccountId,
            ImmutableLedger latestLedger
    ) {
        String closingTxId = generateUniqueId(
                ledgerIdGenerator::generate,
                immutableLedgerRepository::existsById,
                "giao dịch sổ cái"
        );
        String previousHash = latestLedger != null
                ? latestLedger.getRecordHash()
                : ImmutableLedgerHashCalculator.GENESIS_HASH;
        Instant entryTimestamp = resolveMonotonicTimestamp(latestLedger);

        String baseDescription = "Chốt kiểm toán định kỳ năm " + auditYear + " (" + auditId
                + ") - Kết chuyển số dư đầu kỳ năm " + nextPeriodYear + ": "
                + systemBalance.toPlainString() + "đ";
        String description = notes != null ? baseDescription + ". Ghi chú: " + notes : baseDescription;

        String recordHash = ledgerHashCalculator.computeRecordHash(
                previousHash,
                closingTxId,
                clanId,
                null,
                null,
                null,
                null,
                LedgerTransactionType.OpeningBalanceCarryForward,
                ZERO,
                systemBalance,
                description,
                auditorAccountId,
                auditorAccountId,
                entryTimestamp
        );

        ImmutableLedger closingEntry = ImmutableLedger.builder()
                .transactionId(closingTxId)
                .clanId(clanId)
                .eventId(null)
                .adjustmentRequestId(null)
                .disbursementRequestId(null)
                .originalTransactionId(null)
                .transactionType(LedgerTransactionType.OpeningBalanceCarryForward)
                .amount(ZERO)
                .runningBalance(systemBalance)
                .description(description)
                .createdByAccountId(auditorAccountId)
                .approvedByAccountId(auditorAccountId)
                .previousHash(previousHash)
                .recordHash(recordHash)
                .createdAt(entryTimestamp)
                .build();

        ImmutableLedger savedLedger = immutableLedgerRepository.saveAndFlush(closingEntry);

        String effectiveRefTxId = requestedRefTxId != null
                ? requestedRefTxId
                : (latestLedger != null ? latestLedger.getTransactionId() : null);

        LedgerAnnualAudit audit = LedgerAnnualAudit.builder()
                .auditId(auditId)
                .clanId(clanId)
                .auditYear(auditYear)
                .nextPeriodYear(nextPeriodYear)
                .auditDate(entryTimestamp)
                .systemBalance(systemBalance)
                .actualBalance(actualBalance)
                .discrepancyAmount(ZERO)
                .auditStatus(LedgerAnnualAuditStatus.Matched)
                .referenceTransactionId(effectiveRefTxId)
                .adjustmentRequestId(null)
                .closingTransactionId(closingTxId)
                .openingBalanceNextPeriod(systemBalance)
                .auditorAccountId(auditorAccountId)
                .notes(notes)
                .evidenceAttachmentUrl(evidenceUrl)
                .createdAt(entryTimestamp)
                .build();

        LedgerAnnualAudit savedAudit = ledgerAnnualAuditRepository.saveAndFlush(audit);
        log.info("Annual Audit [{}]: Kiểm toán năm {} dòng họ {} khớp số dư ({}đ); đã niêm phong bút toán {} cho kỳ năm {}",
                savedAudit.getAuditId(), auditYear, clanId, systemBalance, closingTxId, nextPeriodYear);

        notifyClanHeadsOnMatchedAudit(savedAudit);

        String auditorDisplayName = resolveAuditorDisplayName(auditorAccountId);
        return toResponse(savedAudit, auditorDisplayName, null, ImmutableLedgerEntryResponse.fromEntity(savedLedger));
    }

    private LedgerAnnualAuditResponse handleDiscrepancyAudit(
            String auditId,
            String clanId,
            int auditYear,
            int nextPeriodYear,
            BigDecimal systemBalance,
            BigDecimal actualBalance,
            BigDecimal discrepancy,
            String requestedRefTxId,
            String notes,
            String evidenceUrl,
            String auditorAccountId,
            ImmutableLedger latestLedger
    ) {
        String targetRefTxId = requestedRefTxId != null
                ? requestedRefTxId
                : (latestLedger != null ? latestLedger.getTransactionId() : null);
        if (targetRefTxId == null) {
            throw new BusinessRuleException(
                    "Không thể tự động sinh đề xuất bù trừ (US-15) do Sổ cái Quỹ chung của dòng họ chưa có bút toán gốc nào. "
                            + "Vui lòng ghi nhận giao dịch kết chuyển ban đầu trước khi thực hiện kiểm toán điều chỉnh.");
        }

        String adjustmentReason = buildAdjustmentReason(auditId, auditYear, systemBalance, actualBalance, discrepancy, notes);
        LedgerAdjustmentRequestResponse proposed = ledgerAdjustmentService.proposeAdjustment(
                new ProposeLedgerAdjustmentRequest(
                        targetRefTxId,
                        discrepancy,
                        adjustmentReason,
                        evidenceUrl
                )
        );

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        LedgerAnnualAudit audit = LedgerAnnualAudit.builder()
                .auditId(auditId)
                .clanId(clanId)
                .auditYear(auditYear)
                .nextPeriodYear(nextPeriodYear)
                .auditDate(now)
                .systemBalance(systemBalance)
                .actualBalance(actualBalance)
                .discrepancyAmount(discrepancy)
                .auditStatus(LedgerAnnualAuditStatus.DiscrepancyDetected)
                .referenceTransactionId(targetRefTxId)
                .adjustmentRequestId(proposed.requestId())
                .closingTransactionId(null)
                .openingBalanceNextPeriod(null)
                .auditorAccountId(auditorAccountId)
                .notes(notes)
                .evidenceAttachmentUrl(evidenceUrl)
                .createdAt(now)
                .build();

        LedgerAnnualAudit savedAudit = ledgerAnnualAuditRepository.saveAndFlush(audit);
        log.info("Annual Audit [{}]: Kiểm toán năm {} dòng họ {} lệch {}đ (sổ cái={}đ, thực kiểm={}đ); đã sinh đề xuất bù trừ {}",
                savedAudit.getAuditId(), auditYear, clanId, discrepancy, systemBalance, actualBalance, proposed.requestId());

        String auditorDisplayName = resolveAuditorDisplayName(auditorAccountId);
        return toResponse(savedAudit, auditorDisplayName, proposed, null);
    }

    private List<LedgerAnnualAuditResponse> enrichAudits(String clanId, List<LedgerAnnualAudit> audits) {
        if (audits == null || audits.isEmpty()) {
            return List.of();
        }

        Set<String> auditorIds = audits.stream()
                .map(LedgerAnnualAudit::getAuditorAccountId)
                .map(this::normalizeBlank)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> auditorNames = auditorIds.isEmpty()
                ? Map.of()
                : Optional.ofNullable(identityRoleCommandService.findAccountDisplayNames(auditorIds)).orElse(Map.of());

        Set<String> adjustmentRequestIds = audits.stream()
                .map(LedgerAnnualAudit::getAdjustmentRequestId)
                .map(this::normalizeBlank)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, LedgerAdjustmentRequest> adjustmentRequestsById = adjustmentRequestIds.isEmpty()
                ? Map.of()
                : nullSafeList(ledgerAdjustmentRequestRepository.findAllById(adjustmentRequestIds)).stream()
                        .filter(Objects::nonNull)
                        .filter(r -> r.getRequestId() != null)
                        .collect(Collectors.toMap(
                                LedgerAdjustmentRequest::getRequestId,
                                Function.identity(),
                                (a, b) -> a,
                                LinkedHashMap::new
                        ));

        Set<String> txIdsToLoad = new LinkedHashSet<>();
        for (LedgerAnnualAudit audit : audits) {
            String closingId = normalizeBlank(audit.getClosingTransactionId());
            if (closingId != null) {
                txIdsToLoad.add(closingId);
            }
        }
        for (LedgerAdjustmentRequest adjReq : adjustmentRequestsById.values()) {
            String origId = normalizeBlank(adjReq.getOriginalTransactionId());
            if (origId != null) {
                txIdsToLoad.add(origId);
            }
        }

        Map<String, ImmutableLedger> ledgerById = txIdsToLoad.isEmpty()
                ? Map.of()
                : nullSafeList(immutableLedgerRepository.findByClanIdAndTransactionIdIn(clanId, txIdsToLoad)).stream()
                        .filter(Objects::nonNull)
                        .filter(l -> l.getTransactionId() != null)
                        .collect(Collectors.toMap(
                                ImmutableLedger::getTransactionId,
                                Function.identity(),
                                (a, b) -> a,
                                LinkedHashMap::new
                        ));

        return audits.stream()
                .map(audit -> {
                    String auditorId = audit.getAuditorAccountId();
                    String displayName = resolveDisplayName(auditorId, auditorNames);

                    String adjReqId = normalizeBlank(audit.getAdjustmentRequestId());
                    LedgerAdjustmentRequestResponse adjResponse = null;
                    if (adjReqId != null) {
                        LedgerAdjustmentRequest adjEntity = adjustmentRequestsById.get(adjReqId);
                        if (adjEntity != null) {
                            ImmutableLedger origTx = ledgerById.get(adjEntity.getOriginalTransactionId());
                            adjResponse = toAdjustmentResponse(adjEntity, origTx);
                        }
                    }

                    String closingTxId = normalizeBlank(audit.getClosingTransactionId());
                    ImmutableLedgerEntryResponse closingResponse = null;
                    if (closingTxId != null) {
                        ImmutableLedger closingEntity = ledgerById.get(closingTxId);
                        if (closingEntity != null) {
                            closingResponse = ImmutableLedgerEntryResponse.fromEntity(closingEntity);
                        }
                    }

                    return toResponse(audit, displayName, adjResponse, closingResponse);
                })
                .toList();
    }

    private void ensureNoPendingRequests(String clanId) {
        long pendingAdjustments = ledgerAdjustmentRequestRepository.countPendingByClanId(clanId);
        long pendingDisbursements = commonFundDisbursementRequestRepository
                .countByClanIdAndApprovalStatus(clanId, CommonFundDisbursementStatus.Pending);

        if (pendingAdjustments > 0 && pendingDisbursements > 0) {
            throw new BusinessRuleException(
                    "Không thể kiểm toán chốt sổ khi dòng họ vẫn còn " + pendingAdjustments
                            + " đề xuất điều chỉnh bù trừ (US-15) và " + pendingDisbursements
                            + " đề xuất chi Quỹ chung (US-60) đang chờ phê duyệt (Pending). Vui lòng xử lý dứt điểm trước.");
        }
        if (pendingAdjustments > 0) {
            throw new BusinessRuleException(
                    "Không thể kiểm toán chốt sổ khi dòng họ vẫn còn " + pendingAdjustments
                            + " đề xuất điều chỉnh bù trừ (US-15) đang chờ phê duyệt (Pending). Vui lòng xử lý dứt điểm trước.");
        }
        if (pendingDisbursements > 0) {
            throw new BusinessRuleException(
                    "Không thể kiểm toán chốt sổ khi dòng họ vẫn còn " + pendingDisbursements
                            + " đề xuất chi Quỹ chung (US-60) đang chờ phê duyệt (Pending). Vui lòng xử lý dứt điểm trước.");
        }
    }

    private void validateAuditorAuthorization() {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)
                || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return;
        }
        throw new AccessDeniedException(
                "Chỉ Trưởng tộc hoặc Quản trị viên mới có quyền thực hiện kiểm toán định kỳ và chốt số dư Sổ cái Quỹ chung");
    }

    private void validateAuditYearRange(int auditYear) {
        if (auditYear < MIN_AUDIT_YEAR || auditYear > MAX_AUDIT_YEAR) {
            throw new BusinessRuleException(
                    "Năm kiểm toán phải nằm trong khoảng từ " + MIN_AUDIT_YEAR + " đến " + MAX_AUDIT_YEAR);
        }
    }

    private void notifyClanHeadsOnMatchedAudit(LedgerAnnualAudit savedAudit) {
        try {
            List<AccountSummaryDto> activeAccounts =
                    identityRoleCommandService.findActiveAccountsByClan(savedAudit.getClanId());
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
                        .clanId(savedAudit.getClanId())
                        .recipientAccountId(acc.accountId())
                        .category(NotificationCategory.Finance)
                        .title("Hoàn tất kiểm toán & chốt số dư Quỹ chung năm " + savedAudit.getAuditYear())
                        .content("Biên bản " + savedAudit.getAuditId() + " đã đối khớp 100% và chốt số dư đầu kỳ năm "
                                + savedAudit.getNextPeriodYear() + ": "
                                + savedAudit.getOpeningBalanceNextPeriod().toPlainString()
                                + "đ (bút toán niêm phong " + savedAudit.getClosingTransactionId() + ").")
                        .referenceType("LEDGER_ANNUAL_AUDIT")
                        .referenceId(savedAudit.getAuditId())
                        .build());
            }
        } catch (RuntimeException ex) {
            log.warn("Annual Audit: Không thể gửi thông báo chốt kiểm toán {}: {}",
                    savedAudit.getAuditId(), ex.getMessage());
        }
    }

    private String resolveAuditorDisplayName(String auditorAccountId) {
        if (normalizeBlank(auditorAccountId) == null) {
            return null;
        }
        try {
            Map<String, String> names =
                    identityRoleCommandService.findAccountDisplayNames(List.of(auditorAccountId));
            return resolveDisplayName(auditorAccountId, names);
        } catch (RuntimeException ex) {
            return auditorAccountId;
        }
    }

    private String resolveDisplayName(String accountId, Map<String, String> accountNames) {
        String normalized = normalizeBlank(accountId);
        if (normalized == null) {
            return null;
        }
        if (accountNames != null) {
            String resolved = normalizeBlank(accountNames.get(normalized));
            if (resolved != null) {
                return resolved;
            }
        }
        return normalized;
    }

    private LedgerAnnualAuditResponse toResponse(
            LedgerAnnualAudit audit,
            String auditorDisplayName,
            LedgerAdjustmentRequestResponse adjustmentResponse,
            ImmutableLedgerEntryResponse closingLedgerEntry
    ) {
        return new LedgerAnnualAuditResponse(
                audit.getAuditId(),
                audit.getClanId(),
                audit.getAuditYear(),
                audit.getNextPeriodYear(),
                audit.getAuditDate(),
                audit.getSystemBalance(),
                audit.getActualBalance(),
                audit.getDiscrepancyAmount(),
                audit.getAuditStatus(),
                audit.getReferenceTransactionId(),
                audit.getAdjustmentRequestId(),
                audit.getClosingTransactionId(),
                audit.getOpeningBalanceNextPeriod(),
                audit.getAuditorAccountId(),
                auditorDisplayName,
                audit.getNotes(),
                audit.getEvidenceAttachmentUrl(),
                audit.getCreatedAt(),
                adjustmentResponse,
                closingLedgerEntry
        );
    }

    private LedgerAdjustmentRequestResponse toAdjustmentResponse(
            LedgerAdjustmentRequest entity,
            ImmutableLedger originalTx
    ) {
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

    private String buildAdjustmentReason(
            String auditId,
            int auditYear,
            BigDecimal systemBalance,
            BigDecimal actualBalance,
            BigDecimal discrepancy,
            String notes
    ) {
        String baseReason = "Tự động sinh từ kiểm toán định kỳ năm " + auditYear + " (" + auditId + "): "
                + "Số dư sổ cái = " + systemBalance.toPlainString() + "đ, "
                + "số tiền thực kiểm = " + actualBalance.toPlainString() + "đ, "
                + "chênh lệch = " + formatSignedAmount(discrepancy) + "đ.";
        String rawReason = notes != null ? baseReason + " Ghi chú: " + notes : baseReason;
        if (rawReason.length() > MAX_REASON_LENGTH) {
            int cutIndex = MAX_REASON_LENGTH;
            if (Character.isHighSurrogate(rawReason.charAt(cutIndex - 1))) {
                cutIndex--;
            }
            return rawReason.substring(0, cutIndex);
        }
        return rawReason;
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
            String entityLabel
    ) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = generator.get();
            if (!existsById.test(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("Không thể khởi tạo mã " + entityLabel + ", vui lòng thử lại");
    }

    private String requireClanId() {
        String rawClanId = TenantContext.getClanId();
        if (rawClanId == null || rawClanId.isBlank()) {
            throw new BusinessRuleException("Thiếu ngữ cảnh dòng họ (clanId) để thực hiện kiểm toán Sổ cái Quỹ chung");
        }
        return rawClanId.trim();
    }

    private String formatSignedAmount(BigDecimal amount) {
        String plain = amount.toPlainString();
        return amount.compareTo(BigDecimal.ZERO) > 0 ? "+" + plain : plain;
    }

    private <T> List<T> nullSafeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
