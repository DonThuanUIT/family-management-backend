package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.response.*;
import com.familyfund.contribution.internal.entity.CashContribution;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.entity.InKindContribution;
import com.familyfund.contribution.internal.repository.CashContributionRepository;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionRepository;
import com.familyfund.contribution.internal.repository.InKindContributionRepository;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionHistoryServiceImpl implements ContributionHistoryService {

    private final ContributionRepository contributionRepository;
    private final CashContributionRepository cashContributionRepository;
    private final InKindContributionRepository inKindContributionRepository;
    private final ContributionMemberDetailRepository contributionMemberDetailRepository;
    private final CashContributionService cashContributionService;
    private final InKindContributionService inKindContributionService;
    private final ReceiptPdfService receiptPdfService;
    private final CurrentUserProvider currentUserProvider;
    private final GenealogyQueryService genealogyQueryService;
    private final EventQueryService eventQueryService;

    @Override
    @Transactional(readOnly = true)
    public MemberContributionHistoryResponse getMyContributionHistory(boolean includeFamily) {
        String currentMemberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new BusinessRuleException("User account is not linked to any member profile"));

        MemberSummaryDto member = genealogyQueryService.findMemberSummary(currentMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found with ID: " + currentMemberId));

        Set<String> targetMemberIds = new HashSet<>();
        targetMemberIds.add(currentMemberId);
        String branchName = null;

        if (includeFamily && member.branchId() != null && member.clanId() != null) {
            List<String> branchMemberIds = genealogyQueryService.getMemberIdsByBranch(member.clanId(), member.branchId());
            if (branchMemberIds != null && !branchMemberIds.isEmpty()) {
                targetMemberIds.addAll(branchMemberIds);
            }
            branchName = genealogyQueryService.getBranchName(member.clanId(), member.branchId()).orElse(null);
        }

        // 1. Fetch contributions where any target member is the direct payer
        List<Contribution> directContributions = contributionRepository.findByPayerMemberIdIn(targetMemberIds);

        // 2. Fetch contributions where any target member has allocated portion
        List<ContributionMemberDetail> allocatedDetails = contributionMemberDetailRepository.findAllByMemberIdIn(targetMemberIds);
        Set<String> indirectContributionIds = allocatedDetails.stream()
                .map(ContributionMemberDetail::getContributionId)
                .collect(Collectors.toSet());

        List<Contribution> indirectContributions = indirectContributionIds.isEmpty()
                ? Collections.emptyList()
                : contributionRepository.findByContributionIdIn(indirectContributionIds);

        // 3. Deduplicate and sort descending by contribution date
        Map<String, Contribution> contributionMap = new HashMap<>();
        directContributions.forEach(c -> contributionMap.put(c.getContributionId(), c));
        indirectContributions.forEach(c -> contributionMap.put(c.getContributionId(), c));

        List<Contribution> allContributions = new ArrayList<>(contributionMap.values());
        allContributions.sort((a, b) -> {
            if (a.getContributionDate() == null || b.getContributionDate() == null) return 0;
            return b.getContributionDate().compareTo(a.getContributionDate());
        });

        // 4. Map to items
        Map<String, String> eventNameCache = new HashMap<>();
        Map<String, String> memberNameCache = new HashMap<>();
        memberNameCache.put(member.memberId(), member.fullName());

        List<MemberContributionHistoryItemResponse> items = new ArrayList<>();
        BigDecimal totalPersonalContributed = BigDecimal.ZERO;
        BigDecimal totalAllocated = BigDecimal.ZERO;

        for (Contribution c : allContributions) {
            String eventName = eventNameCache.computeIfAbsent(c.getEventId(), id ->
                    eventQueryService.findEventSummary(id).map(EventSummaryDto::eventName).orElse("Sự kiện " + id));

            String payerName = memberNameCache.computeIfAbsent(c.getPayerMemberId(), id ->
                    genealogyQueryService.findMemberSummary(id).map(MemberSummaryDto::fullName).orElse("Thành viên " + id));

            String receiptNumber = null;
            String itemName = null;
            BigDecimal quantity = null;
            String unit = null;
            PaymentMethod paymentMethod = null;
            String status = "Completed";

            if (c.getContributionType() == ContributionType.Cash) {
                Optional<CashContribution> cashOpt = cashContributionRepository.findById(c.getContributionId());
                if (cashOpt.isPresent()) {
                    CashContribution cash = cashOpt.get();
                    receiptNumber = cash.getReceiptNumber();
                    paymentMethod = cash.getPaymentMethod();
                }
            } else {
                Optional<InKindContribution> inKindOpt = inKindContributionRepository.findById(c.getContributionId());
                if (inKindOpt.isPresent()) {
                    InKindContribution inKind = inKindOpt.get();
                    receiptNumber = inKind.getReceiptNumber();
                    itemName = inKind.getItemName();
                    quantity = inKind.getQuantity();
                    unit = inKind.getUnit();
                    status = inKind.getProcessingStatus() != null ? inKind.getProcessingStatus().name() : "Pending";
                }
            }

            // Calculate allocated portion for the target member(s)
            List<ContributionMemberDetail> details = contributionMemberDetailRepository.findAllByContributionId(c.getContributionId());
            BigDecimal myAllocated = details.stream()
                    .filter(d -> targetMemberIds.contains(d.getMemberId()))
                    .map(ContributionMemberDetail::getRecordedAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (details.isEmpty() && targetMemberIds.contains(c.getPayerMemberId())) {
                myAllocated = c.getTotalMonetaryValue();
            }

            if (c.getPayerMemberId().equals(currentMemberId)) {
                totalPersonalContributed = totalPersonalContributed.add(c.getTotalMonetaryValue());
            }
            totalAllocated = totalAllocated.add(myAllocated);

            items.add(new MemberContributionHistoryItemResponse(
                    c.getContributionId(),
                    receiptNumber,
                    c.getEventId(),
                    eventName,
                    c.getPayerMemberId(),
                    payerName,
                    c.getContributionType(),
                    paymentMethod,
                    itemName,
                    quantity,
                    unit,
                    c.getTotalMonetaryValue(),
                    myAllocated,
                    status,
                    c.getContributionDate()
            ));
        }

        return new MemberContributionHistoryResponse(
                member.memberId(),
                member.fullName(),
                member.branchId(),
                branchName,
                member.clanId(),
                totalPersonalContributed,
                totalAllocated,
                items
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadReceiptPdf(String receiptNumber) {
        String clanId = TenantContext.getClanId();
        String clanName = (clanId != null) ? genealogyQueryService.getClanName(clanId).orElse(null) : null;

        if (cashContributionRepository.existsByReceiptNumber(receiptNumber)) {
            ElectronicReceiptResponse receipt = cashContributionService.getReceiptByReceiptNumber(receiptNumber);
            return receiptPdfService.generateCashReceiptPdf(receipt, clanName);
        } else if (inKindContributionRepository.existsByReceiptNumber(receiptNumber)) {
            InKindElectronicReceiptResponse receipt = inKindContributionService.getReceiptByReceiptNumber(receiptNumber);
            return receiptPdfService.generateInKindReceiptPdf(receipt, clanName);
        } else {
            throw new ResourceNotFoundException("Electronic receipt not found with number: " + receiptNumber);
        }
    }
}
