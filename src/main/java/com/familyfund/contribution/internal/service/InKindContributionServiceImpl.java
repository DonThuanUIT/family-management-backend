package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.ConversionMethod;
import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import com.familyfund.contribution.internal.dto.request.MemberAllocationRequest;
import com.familyfund.contribution.internal.dto.request.RecordInKindContributionRequest;
import com.familyfund.contribution.internal.dto.response.InKindContributionDetailResponse;
import com.familyfund.contribution.internal.dto.response.InKindElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.MemberAllocationResponse;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.entity.EventItemBenchmark;
import com.familyfund.contribution.internal.entity.InKindContribution;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionRepository;
import com.familyfund.contribution.internal.repository.EventItemBenchmarkRepository;
import com.familyfund.contribution.internal.repository.InKindContributionRepository;
import com.familyfund.contribution.internal.util.ContributionIdGenerator;
import com.familyfund.contribution.internal.util.MoneyToWordsConverter;
import com.familyfund.contribution.internal.util.ReceiptNumberGenerator;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InKindContributionServiceImpl implements InKindContributionService {

    private final ContributionRepository contributionRepository;
    private final InKindContributionRepository inKindContributionRepository;
    private final ContributionMemberDetailRepository contributionMemberDetailRepository;
    private final EventItemBenchmarkRepository benchmarkRepository;
    private final ContributionIdGenerator contributionIdGenerator;
    private final ReceiptNumberGenerator receiptNumberGenerator;
    private final GenealogyQueryService genealogyQueryService;
    private final EventQueryService eventQueryService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InKindElectronicReceiptResponse recordInKindContribution(RecordInKindContributionRequest request) {
        log.info("Processing in-kind contribution for event {} by payer {}", request.eventId(), request.payerMemberId());

        String currentClanId = TenantContext.getClanId();

        // 1. Verify Event existence, tenancy, and lifecycle status
        EventSummaryDto eventSummary = eventQueryService.findEventSummary(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + request.eventId()));

        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(eventSummary.clanId())) {
            throw new BusinessRuleException("Event does not belong to the current clan");
        }

        if (eventSummary.status() == EventStatus.Closed) {
            throw new BusinessRuleException("Cannot record contribution for a closed event");
        }

        // 2. Verify Payer Member existence and tenancy
        MemberSummaryDto payerMember = genealogyQueryService.findMemberSummary(request.payerMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Payer member not found with ID: " + request.payerMemberId()));

        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(payerMember.clanId())) {
            throw new BusinessRuleException("Payer member does not belong to the current clan");
        }

        // 3. Resolve Receiver Account ID
        String receiverAccountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new BusinessRuleException("Receiver account ID could not be identified"));

        // 4. Validate Conversion Method & Value (tinhGiaTriQuyDoi)
        BigDecimal convertedValue = request.convertedMonetaryValue();
        if (convertedValue == null || convertedValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Converted monetary value must be greater than or equal to 0");
        }

        if (request.conversionMethod() == ConversionMethod.BtcBenchmark && request.benchmarkId() != null && !request.benchmarkId().isBlank()) {
            EventItemBenchmark benchmark = benchmarkRepository.findByBenchmarkIdAndEventId(request.benchmarkId(), request.eventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Benchmark not found with ID: " + request.benchmarkId() + " for event: " + request.eventId()));

            BigDecimal expectedValue = benchmark.getUnitPrice().multiply(request.quantity());
            if (convertedValue.compareTo(expectedValue) != 0) {
                throw new BusinessRuleException(String.format(
                        "Converted value (%s) does not match benchmark unit price (%s) * quantity (%s) = %s",
                        convertedValue, benchmark.getUnitPrice(), request.quantity(), expectedValue
                ));
            }
        }

        // 5. Generate Unique IDs (#69 & Task #72)
        String contributionId = generateUniqueContributionId();
        String receiptNumber = generateUniqueReceiptNumber();

        // 6. Handle Allocations (#70)
        List<ContributionMemberDetail> memberDetails = new ArrayList<>();
        List<MemberAllocationResponse> allocationResponses = new ArrayList<>();

        if (request.allocations() == null || request.allocations().isEmpty()) {
            // Case 1: Single payer takes 100% contribution allocation
            memberDetails.add(ContributionMemberDetail.builder()
                    .contributionId(contributionId)
                    .memberId(request.payerMemberId())
                    .recordedAmount(convertedValue)
                    .build());

            allocationResponses.add(new MemberAllocationResponse(
                    request.payerMemberId(),
                    payerMember.fullName(),
                    convertedValue
            ));
        } else {
            // Case 2: Multiple descendants joint contribution
            Set<String> seenMembers = new HashSet<>();
            BigDecimal totalAllocated = BigDecimal.ZERO;

            for (MemberAllocationRequest alloc : request.allocations()) {
                if (!seenMembers.add(alloc.memberId())) {
                    throw new BusinessRuleException("Duplicate member in allocation list: " + alloc.memberId());
                }
                if (alloc.amount() == null || alloc.amount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessRuleException("Allocated amount for member " + alloc.memberId() + " must be greater than 0");
                }
                totalAllocated = totalAllocated.add(alloc.amount());
            }

            if (totalAllocated.compareTo(convertedValue) != 0) {
                throw new BusinessRuleException(String.format(
                        "Total allocated amount (%s) does not match converted monetary value (%s)",
                        totalAllocated, convertedValue
                ));
            }

            for (MemberAllocationRequest alloc : request.allocations()) {
                MemberSummaryDto mSummary = genealogyQueryService.findMemberSummary(alloc.memberId())
                        .orElseThrow(() -> new ResourceNotFoundException("Allocated member not found with ID: " + alloc.memberId()));

                if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(mSummary.clanId())) {
                    throw new BusinessRuleException("Allocated member " + alloc.memberId() + " does not belong to the current clan");
                }

                memberDetails.add(ContributionMemberDetail.builder()
                        .contributionId(contributionId)
                        .memberId(alloc.memberId())
                        .recordedAmount(alloc.amount())
                        .build());

                allocationResponses.add(new MemberAllocationResponse(
                        alloc.memberId(),
                        mSummary.fullName(),
                        alloc.amount()
                ));
            }
        }

        Instant now = Instant.now();

        // 7. Persist Parent Contribution
        Contribution contribution = Contribution.builder()
                .contributionId(contributionId)
                .eventId(request.eventId())
                .payerMemberId(request.payerMemberId())
                .receiverAccountId(receiverAccountId)
                .contributionDate(now)
                .contributionType(ContributionType.InKind)
                .totalMonetaryValue(convertedValue)
                .build();
        contributionRepository.save(contribution);

        // 8. Persist Child InKindContribution Record
        InKindContribution inKindContribution = InKindContribution.builder()
                .contributionId(contributionId)
                .itemName(request.itemName().trim())
                .quantity(request.quantity())
                .unit(request.unit().trim())
                .estimatedValue(request.estimatedValue() != null ? request.estimatedValue() : BigDecimal.ZERO)
                .conversionMethod(request.conversionMethod())
                .convertedMonetaryValue(convertedValue)
                .processingStatus(InKindProcessingStatus.Pending)
                .receiptNumber(receiptNumber)
                .build();
        inKindContributionRepository.save(inKindContribution);

        // 9. Persist Member Allocations
        contributionMemberDetailRepository.saveAll(memberDetails);

        log.info("Successfully recorded in-kind contribution {} with receipt {}", contributionId, receiptNumber);

        // 10. Generate Words Representation and Return
        String amountInWords = MoneyToWordsConverter.toVietnameseWords(convertedValue);

        return new InKindElectronicReceiptResponse(
                receiptNumber,
                contributionId,
                request.eventId(),
                eventSummary.eventName(),
                request.payerMemberId(),
                payerMember.fullName(),
                receiverAccountId,
                inKindContribution.getItemName(),
                inKindContribution.getQuantity(),
                inKindContribution.getUnit(),
                inKindContribution.getConversionMethod(),
                inKindContribution.getConvertedMonetaryValue(),
                amountInWords,
                inKindContribution.getProcessingStatus(),
                now,
                allocationResponses
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InKindElectronicReceiptResponse getReceiptByReceiptNumber(String receiptNumber) {
        InKindContribution inKindContribution = inKindContributionRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with number: " + receiptNumber));

        Contribution contribution = contributionRepository.findById(inKindContribution.getContributionId())
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found with ID: " + inKindContribution.getContributionId()));

        EventSummaryDto eventSummary = eventQueryService.findEventSummary(contribution.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + contribution.getEventId()));

        String currentClanId = TenantContext.getClanId();
        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(eventSummary.clanId())) {
            throw new BusinessRuleException("Receipt does not belong to the current clan");
        }

        MemberSummaryDto payerMember = genealogyQueryService.findMemberSummary(contribution.getPayerMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Payer member not found with ID: " + contribution.getPayerMemberId()));

        List<ContributionMemberDetail> details = contributionMemberDetailRepository.findAllByContributionId(contribution.getContributionId());
        List<MemberAllocationResponse> allocations = details.stream()
                .map(d -> {
                    String memberName = genealogyQueryService.findMemberSummary(d.getMemberId())
                            .map(MemberSummaryDto::fullName)
                            .orElse("Unknown Member");
                    return new MemberAllocationResponse(d.getMemberId(), memberName, d.getRecordedAmount());
                })
                .toList();

        String amountInWords = MoneyToWordsConverter.toVietnameseWords(inKindContribution.getConvertedMonetaryValue());

        return new InKindElectronicReceiptResponse(
                inKindContribution.getReceiptNumber(),
                contribution.getContributionId(),
                contribution.getEventId(),
                eventSummary.eventName(),
                contribution.getPayerMemberId(),
                payerMember.fullName(),
                contribution.getReceiverAccountId(),
                inKindContribution.getItemName(),
                inKindContribution.getQuantity(),
                inKindContribution.getUnit(),
                inKindContribution.getConversionMethod(),
                inKindContribution.getConvertedMonetaryValue(),
                amountInWords,
                inKindContribution.getProcessingStatus(),
                contribution.getContributionDate(),
                allocations
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InKindContributionDetailResponse getInKindContributionById(String contributionId) {
        InKindContribution inKindContribution = inKindContributionRepository.findById(contributionId)
                .orElseThrow(() -> new ResourceNotFoundException("In-kind contribution not found with ID: " + contributionId));

        Contribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found with ID: " + contributionId));

        EventSummaryDto eventSummary = eventQueryService.findEventSummary(contribution.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + contribution.getEventId()));

        String currentClanId = TenantContext.getClanId();
        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(eventSummary.clanId())) {
            throw new BusinessRuleException("Contribution does not belong to the current clan");
        }

        MemberSummaryDto payerMember = genealogyQueryService.findMemberSummary(contribution.getPayerMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Payer member not found with ID: " + contribution.getPayerMemberId()));

        List<ContributionMemberDetail> details = contributionMemberDetailRepository.findAllByContributionId(contributionId);
        List<MemberAllocationResponse> allocations = details.stream()
                .map(d -> {
                    String memberName = genealogyQueryService.findMemberSummary(d.getMemberId())
                            .map(MemberSummaryDto::fullName)
                            .orElse("Unknown Member");
                    return new MemberAllocationResponse(d.getMemberId(), memberName, d.getRecordedAmount());
                })
                .toList();

        String amountInWords = MoneyToWordsConverter.toVietnameseWords(inKindContribution.getConvertedMonetaryValue());

        return new InKindContributionDetailResponse(
                inKindContribution.getContributionId(),
                inKindContribution.getReceiptNumber(),
                contribution.getEventId(),
                eventSummary.eventName(),
                contribution.getPayerMemberId(),
                payerMember.fullName(),
                contribution.getReceiverAccountId(),
                inKindContribution.getItemName(),
                inKindContribution.getQuantity(),
                inKindContribution.getUnit(),
                inKindContribution.getEstimatedValue(),
                inKindContribution.getConversionMethod(),
                inKindContribution.getConvertedMonetaryValue(),
                amountInWords,
                inKindContribution.getProcessingStatus(),
                contribution.getContributionDate(),
                allocations
        );
    }

    private String generateUniqueContributionId() {
        for (int i = 0; i < 5; i++) {
            String candidate = contributionIdGenerator.generate();
            if (!contributionRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique contribution ID after multiple attempts");
    }

    private String generateUniqueReceiptNumber() {
        for (int i = 0; i < 5; i++) {
            String candidate = receiptNumberGenerator.generate();
            if (!inKindContributionRepository.existsByReceiptNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique receipt number after multiple attempts");
    }
}
