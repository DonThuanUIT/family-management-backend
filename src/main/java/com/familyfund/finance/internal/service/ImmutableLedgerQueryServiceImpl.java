package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.response.LedgerFlowSummaryResponse;
import com.familyfund.finance.internal.dto.response.LedgerHistoryItemResponse;
import com.familyfund.finance.internal.dto.response.LedgerHistoryPageResponse;
import com.familyfund.finance.internal.dto.response.LinkedLedgerTransactionDto;
import com.familyfund.finance.internal.entity.CommonFundDisbursementRequest;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.repository.CommonFundDisbursementRequestRepository;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
import com.familyfund.finance.internal.repository.LedgerFilteredAggregates;
import com.familyfund.finance.internal.repository.LedgerSearchCriteria;
import com.familyfund.identity.api.IdentityRoleCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
import java.util.stream.Collectors;

/**
 * Read-only query service implementation for common fund immutable ledger history,
 * cash-flow summary metrics, and bidirectional original-to-adjustment linking (US-62).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImmutableLedgerQueryServiceImpl implements ImmutableLedgerQueryService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ImmutableLedgerRepository immutableLedgerRepository;
    private final LedgerAdjustmentRequestRepository ledgerAdjustmentRequestRepository;
    private final CommonFundDisbursementRequestRepository commonFundDisbursementRequestRepository;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final EventQueryService eventQueryService;

    @Override
    @Transactional(readOnly = true)
    public LedgerHistoryPageResponse searchLedgerHistory(
            String from,
            String to,
            List<LedgerTransactionType> transactionTypes,
            String eventId,
            String keyword,
            Boolean onlyWithAdjustments,
            String sortDirection,
            int page,
            int size
    ) {
        String clanId = requireClanId();

        if (page < 0) {
            throw new BusinessRuleException("Chỉ số trang (page) không được âm");
        }
        if (size < 1 || size > 100) {
            throw new BusinessRuleException("Kích thước trang (size) phải nằm trong khoảng từ 1 đến 100");
        }

        Sort.Direction direction = parseSortDirection(sortDirection);
        Instant fromInstant = parseBoundaryInstant(from, "from", false);
        Instant toInstant = parseBoundaryInstant(to, "to", true);

        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new BusinessRuleException("Thời điểm bắt đầu (from) không được lớn hơn thời điểm kết thúc (to)");
        }

        List<LedgerTransactionType> normalizedTypes = normalizeTransactionTypes(transactionTypes);
        String normalizedEventId = normalizeBlank(eventId);
        String normalizedKeyword = normalizeBlank(keyword);

        LedgerSearchCriteria criteria = new LedgerSearchCriteria(
                clanId,
                fromInstant,
                toInstant,
                normalizedTypes.isEmpty() ? null : normalizedTypes,
                normalizedEventId,
                normalizedKeyword,
                onlyWithAdjustments
        );

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt", "transactionId"));
        Page<ImmutableLedger> entryPage = immutableLedgerRepository.searchLedgerEntries(criteria, pageable);
        if (entryPage == null) {
            entryPage = Page.empty(pageable);
        }

        List<LedgerHistoryItemResponse> items = enrichEntries(clanId, entryPage.getContent());
        LedgerFlowSummaryResponse summary = buildFlowSummary(clanId, criteria, fromInstant, toInstant, entryPage);

        return new LedgerHistoryPageResponse(
                clanId,
                items,
                entryPage.getNumber(),
                entryPage.getSize(),
                entryPage.getTotalElements(),
                entryPage.getTotalPages(),
                summary
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerHistoryItemResponse getTransactionDetail(String transactionId) {
        String clanId = requireClanId();
        String normalizedId = normalizeBlank(transactionId);
        if (normalizedId == null) {
            throw new ResourceNotFoundException("Giao dịch sổ cái", "transactionId", transactionId);
        }

        ImmutableLedger entry = immutableLedgerRepository.findByTransactionIdAndClanId(normalizedId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Giao dịch sổ cái", "transactionId", transactionId));

        return enrichEntries(clanId, List.of(entry)).get(0);
    }

    private LedgerFlowSummaryResponse buildFlowSummary(
            String clanId,
            LedgerSearchCriteria criteria,
            Instant fromInstant,
            Instant toInstant,
            Page<ImmutableLedger> entryPage
    ) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Optional<ImmutableLedger> latestOpt =
                immutableLedgerRepository.findFirstByClanIdOrderByCreatedAtDescTransactionIdDesc(clanId);

        BigDecimal currentFundBalance = latestOpt
                .map(ImmutableLedger::getRunningBalance)
                .filter(Objects::nonNull)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        String latestTransactionId = latestOpt.map(ImmutableLedger::getTransactionId).orElse(null);
        Instant latestTransactionAt = latestOpt.map(ImmutableLedger::getCreatedAt).orElse(null);

        BigDecimal openingBalanceBeforePeriod;
        if (fromInstant != null) {
            openingBalanceBeforePeriod = immutableLedgerRepository
                    .findFirstByClanIdAndCreatedAtLessThanOrderByCreatedAtDescTransactionIdDesc(clanId, fromInstant)
                    .map(ImmutableLedger::getRunningBalance)
                    .filter(Objects::nonNull)
                    .orElse(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            openingBalanceBeforePeriod = zero;
        }

        BigDecimal closingBalanceAtPeriodEnd;
        if (toInstant != null) {
            closingBalanceAtPeriodEnd = immutableLedgerRepository
                    .findFirstByClanIdAndCreatedAtLessThanEqualOrderByCreatedAtDescTransactionIdDesc(clanId, toInstant)
                    .map(ImmutableLedger::getRunningBalance)
                    .filter(Objects::nonNull)
                    .orElse(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            closingBalanceAtPeriodEnd = currentFundBalance;
        }

        LedgerFilteredAggregates aggregates = immutableLedgerRepository.aggregateFilteredFlow(criteria);
        long filteredCount = aggregates != null ? aggregates.totalCount() : entryPage.getTotalElements();
        BigDecimal filteredInflow = (aggregates != null && aggregates.totalInflow() != null
                ? aggregates.totalInflow()
                : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal filteredOutflow = (aggregates != null && aggregates.totalOutflow() != null
                ? aggregates.totalOutflow()
                : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal filteredNet = (aggregates != null && aggregates.netAmount() != null
                ? aggregates.netAmount()
                : filteredInflow.subtract(filteredOutflow)).setScale(2, RoundingMode.HALF_UP);

        return new LedgerFlowSummaryResponse(
                currentFundBalance,
                latestTransactionId,
                latestTransactionAt,
                openingBalanceBeforePeriod,
                closingBalanceAtPeriodEnd,
                filteredCount,
                filteredInflow,
                filteredOutflow,
                filteredNet
        );
    }

    private List<LedgerHistoryItemResponse> enrichEntries(String clanId, List<ImmutableLedger> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        Set<String> primaryTxIds = entries.stream()
                .map(ImmutableLedger::getTransactionId)
                .map(this::normalizeBlank)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 1. Batch fetch compensating AdjustmentTransactions pointing to any of the primary entries
        List<ImmutableLedger> adjustmentEntries = primaryTxIds.isEmpty()
                ? List.of()
                : nullSafeList(immutableLedgerRepository
                        .findByClanIdAndOriginalTransactionIdInOrderByCreatedAtAscTransactionIdAsc(clanId, primaryTxIds));

        Map<String, List<ImmutableLedger>> adjustmentsByOriginalId = adjustmentEntries.stream()
                .filter(e -> normalizeBlank(e.getOriginalTransactionId()) != null)
                .collect(Collectors.groupingBy(
                        e -> e.getOriginalTransactionId().trim(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 2. Batch fetch original transactions referenced by any adjustment entries in the current list
        Set<String> originalTxIds = entries.stream()
                .map(ImmutableLedger::getOriginalTransactionId)
                .map(this::normalizeBlank)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ImmutableLedger> originalEntries = originalTxIds.isEmpty()
                ? List.of()
                : nullSafeList(immutableLedgerRepository.findByClanIdAndTransactionIdIn(clanId, originalTxIds));

        Map<String, ImmutableLedger> originalById = new LinkedHashMap<>();
        for (ImmutableLedger entry : entries) {
            if (entry.getTransactionId() != null && originalTxIds.contains(entry.getTransactionId())) {
                originalById.put(entry.getTransactionId(), entry);
            }
        }
        for (ImmutableLedger orig : originalEntries) {
            if (orig.getTransactionId() != null) {
                originalById.put(orig.getTransactionId(), orig);
            }
        }

        // 3. Collect all adjustmentRequestIds across primary, original, and adjustment entries
        Set<String> adjustmentRequestIds = new LinkedHashSet<>();
        collectAdjustmentRequestIds(entries, adjustmentRequestIds);
        collectAdjustmentRequestIds(originalEntries, adjustmentRequestIds);
        collectAdjustmentRequestIds(adjustmentEntries, adjustmentRequestIds);

        Map<String, LedgerAdjustmentRequest> adjustmentRequestById = adjustmentRequestIds.isEmpty()
                ? Map.of()
                : nullSafeList(ledgerAdjustmentRequestRepository.findAllById(adjustmentRequestIds)).stream()
                        .filter(Objects::nonNull)
                        .filter(r -> r.getRequestId() != null)
                        .collect(Collectors.toMap(
                                LedgerAdjustmentRequest::getRequestId,
                                Function.identity(),
                                (a, b) -> a
                        ));

        // 4. Collect all disbursementRequestIds across primary entries
        Set<String> disbursementRequestIds = entries.stream()
                .map(ImmutableLedger::getDisbursementRequestId)
                .map(this::normalizeBlank)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, CommonFundDisbursementRequest> disbursementRequestById = disbursementRequestIds.isEmpty()
                ? Map.of()
                : nullSafeList(commonFundDisbursementRequestRepository.findAllById(disbursementRequestIds)).stream()
                        .filter(Objects::nonNull)
                        .filter(r -> r.getRequestId() != null)
                        .collect(Collectors.toMap(
                                CommonFundDisbursementRequest::getRequestId,
                                Function.identity(),
                                (a, b) -> a
                        ));

        // 5. Collect all eventIds across primary entries
        Set<String> eventIds = entries.stream()
                .map(ImmutableLedger::getEventId)
                .map(this::normalizeBlank)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> eventNames = eventIds.isEmpty()
                ? Map.of()
                : Optional.ofNullable(eventQueryService.findEventNamesByIds(eventIds)).orElse(Map.of());

        // 6. Collect all createdByAccountId and approvedByAccountId across primary, original, and adjustment entries
        Set<String> accountIds = new LinkedHashSet<>();
        collectAccountIds(entries, accountIds);
        collectAccountIds(originalEntries, accountIds);
        collectAccountIds(adjustmentEntries, accountIds);

        Map<String, String> accountNames = accountIds.isEmpty()
                ? Map.of()
                : Optional.ofNullable(identityRoleCommandService.findAccountDisplayNames(accountIds)).orElse(Map.of());

        // 7. Batch count pending adjustment requests per originalTransactionId
        List<LedgerAdjustmentRequest> pendingRequests = primaryTxIds.isEmpty()
                ? List.of()
                : nullSafeList(ledgerAdjustmentRequestRepository.findByOriginalTransactionIdInAndApprovalStatus(
                        primaryTxIds, LedgerAdjustmentStatus.Pending));

        Map<String, Integer> pendingCountByOriginalId = new LinkedHashMap<>();
        for (LedgerAdjustmentRequest pendingReq : pendingRequests) {
            if (pendingReq != null && normalizeBlank(pendingReq.getOriginalTransactionId()) != null) {
                String origId = pendingReq.getOriginalTransactionId().trim();
                pendingCountByOriginalId.merge(origId, 1, Integer::sum);
            }
        }

        // 8. Map each primary entry to LedgerHistoryItemResponse
        List<LedgerHistoryItemResponse> result = new ArrayList<>(entries.size());
        for (ImmutableLedger entry : entries) {
            String txId = entry.getTransactionId();
            String origTxId = normalizeBlank(entry.getOriginalTransactionId());
            boolean isAdjustment = entry.getTransactionType() == LedgerTransactionType.AdjustmentTransaction
                    || origTxId != null;

            List<ImmutableLedger> rawAdjustments = adjustmentsByOriginalId.getOrDefault(txId, List.of());
            List<LinkedLedgerTransactionDto> adjustmentDtos = rawAdjustments.stream()
                    .map(adj -> toLinkedDto(adj, adjustmentRequestById, accountNames))
                    .toList();

            boolean hasAdjustments = !adjustmentDtos.isEmpty();
            int adjustmentCount = adjustmentDtos.size();

            BigDecimal baseAmount = entry.getAmount() != null ? entry.getAmount() : BigDecimal.ZERO;
            BigDecimal sumOfAdjustments = rawAdjustments.stream()
                    .map(ImmutableLedger::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netAdjustedAmount = baseAmount.add(sumOfAdjustments).setScale(2, RoundingMode.HALF_UP);

            int pendingAdjustmentRequestCount = pendingCountByOriginalId.getOrDefault(txId, 0);

            ImmutableLedger origEntity = origTxId != null ? originalById.get(origTxId) : null;
            LinkedLedgerTransactionDto originalTransactionDto = origEntity != null
                    ? toLinkedDto(origEntity, adjustmentRequestById, accountNames)
                    : null;

            String adjReqId = normalizeBlank(entry.getAdjustmentRequestId());
            LedgerAdjustmentRequest adjReq = adjReqId != null ? adjustmentRequestById.get(adjReqId) : null;

            String disbReqId = normalizeBlank(entry.getDisbursementRequestId());
            CommonFundDisbursementRequest disbReq = disbReqId != null ? disbursementRequestById.get(disbReqId) : null;

            String evId = normalizeBlank(entry.getEventId());
            String eventName = evId != null ? eventNames.get(evId) : null;

            result.add(new LedgerHistoryItemResponse(
                    txId,
                    entry.getClanId(),
                    entry.getEventId(),
                    eventName,
                    entry.getAdjustmentRequestId(),
                    adjReq != null ? adjReq.getAdjustmentReason() : null,
                    adjReq != null ? adjReq.getEvidenceAttachmentUrl() : null,
                    entry.getDisbursementRequestId(),
                    disbReq != null ? disbReq.getTitle() : null,
                    disbReq != null ? disbReq.getCategory() : null,
                    entry.getOriginalTransactionId(),
                    entry.getTransactionType(),
                    entry.getAmount(),
                    entry.getRunningBalance(),
                    entry.getDescription(),
                    entry.getCreatedByAccountId(),
                    resolveAccountDisplayName(entry.getCreatedByAccountId(), accountNames),
                    entry.getApprovedByAccountId(),
                    resolveAccountDisplayName(entry.getApprovedByAccountId(), accountNames),
                    entry.getPreviousHash(),
                    entry.getRecordHash(),
                    entry.getCreatedAt(),
                    isAdjustment,
                    hasAdjustments,
                    adjustmentCount,
                    netAdjustedAmount,
                    pendingAdjustmentRequestCount,
                    originalTransactionDto,
                    adjustmentDtos
            ));
        }
        return result;
    }

    private LinkedLedgerTransactionDto toLinkedDto(
            ImmutableLedger entry,
            Map<String, LedgerAdjustmentRequest> adjustmentRequestById,
            Map<String, String> accountNames
    ) {
        String adjReqId = normalizeBlank(entry.getAdjustmentRequestId());
        LedgerAdjustmentRequest adjReq = adjReqId != null ? adjustmentRequestById.get(adjReqId) : null;

        return new LinkedLedgerTransactionDto(
                entry.getTransactionId(),
                entry.getTransactionType(),
                entry.getAmount(),
                entry.getRunningBalance(),
                entry.getDescription(),
                entry.getAdjustmentRequestId(),
                adjReq != null ? adjReq.getAdjustmentReason() : null,
                adjReq != null ? adjReq.getEvidenceAttachmentUrl() : null,
                entry.getCreatedByAccountId(),
                resolveAccountDisplayName(entry.getCreatedByAccountId(), accountNames),
                entry.getApprovedByAccountId(),
                resolveAccountDisplayName(entry.getApprovedByAccountId(), accountNames),
                entry.getCreatedAt()
        );
    }

    private void collectAdjustmentRequestIds(Collection<ImmutableLedger> entries, Set<String> target) {
        if (entries == null) {
            return;
        }
        for (ImmutableLedger entry : entries) {
            if (entry != null) {
                String reqId = normalizeBlank(entry.getAdjustmentRequestId());
                if (reqId != null) {
                    target.add(reqId);
                }
            }
        }
    }

    private void collectAccountIds(Collection<ImmutableLedger> entries, Set<String> target) {
        if (entries == null) {
            return;
        }
        for (ImmutableLedger entry : entries) {
            if (entry != null) {
                String createdBy = normalizeBlank(entry.getCreatedByAccountId());
                if (createdBy != null) {
                    target.add(createdBy);
                }
                String approvedBy = normalizeBlank(entry.getApprovedByAccountId());
                if (approvedBy != null) {
                    target.add(approvedBy);
                }
            }
        }
    }

    private String resolveAccountDisplayName(String accountId, Map<String, String> accountNames) {
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

    private String requireClanId() {
        String rawClanId = TenantContext.getClanId();
        if (rawClanId == null || rawClanId.isBlank()) {
            throw new BusinessRuleException("Thiếu ngữ cảnh dòng họ (clanId) để tra cứu Sổ cái Quỹ chung");
        }
        return rawClanId.trim();
    }

    private Sort.Direction parseSortDirection(String sortDirection) {
        String normalized = normalizeBlank(sortDirection);
        if (normalized == null) {
            return Sort.Direction.DESC;
        }
        if ("ASC".equalsIgnoreCase(normalized)) {
            return Sort.Direction.ASC;
        }
        if ("DESC".equalsIgnoreCase(normalized)) {
            return Sort.Direction.DESC;
        }
        throw new BusinessRuleException("Hướng sắp xếp (sortDirection) chỉ chấp nhận ASC hoặc DESC");
    }

    private Instant parseBoundaryInstant(String rawValue, String paramName, boolean endOfDay) {
        String trimmed = normalizeBlank(rawValue);
        if (trimmed == null) {
            return null;
        }
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Fall through to OffsetDateTime
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // Fall through to LocalDate
        }
        try {
            LocalDate localDate = LocalDate.parse(trimmed);
            if (endOfDay) {
                return localDate.atTime(23, 59, 59, 999_999_000).atZone(VIETNAM_ZONE).toInstant();
            }
            return localDate.atStartOfDay(VIETNAM_ZONE).toInstant();
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException(
                    "Tham số thời gian '" + paramName + "' không đúng định dạng (hỗ trợ yyyy-MM-dd hoặc ISO-8601)");
        }
    }

    private List<LedgerTransactionType> normalizeTransactionTypes(List<LedgerTransactionType> transactionTypes) {
        if (transactionTypes == null || transactionTypes.isEmpty()) {
            return List.of();
        }
        return transactionTypes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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
