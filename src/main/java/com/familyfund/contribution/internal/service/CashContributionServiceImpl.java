package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.internal.dto.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.MemberAllocationRequest;
import com.familyfund.contribution.internal.dto.MemberAllocationResponse;
import com.familyfund.contribution.internal.dto.RecordCashContributionRequest;
import com.familyfund.contribution.internal.entity.CashContribution;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.repository.CashContributionRepository;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionRepository;
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
public class CashContributionServiceImpl implements CashContributionService {

    private final ContributionRepository contributionRepository;
    private final CashContributionRepository cashContributionRepository;
    private final ContributionMemberDetailRepository contributionMemberDetailRepository;
    private final ContributionIdGenerator contributionIdGenerator;
    private final ReceiptNumberGenerator receiptNumberGenerator;
    private final GenealogyQueryService genealogyQueryService;
    private final EventQueryService eventQueryService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ElectronicReceiptResponse recordCashContribution(RecordCashContributionRequest request) {
        log.info("Processing cash contribution for event {} by payer {}", request.eventId(), request.payerMemberId());

        String currentClanId = TenantContext.getClanId();

        // 1. Verify Event existence, tenancy, and lifecycle status
        EventSummaryDto eventSummary = eventQueryService.findEventSummary(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Sự kiện không tồn tại với ID: " + request.eventId()));

        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(eventSummary.clanId())) {
            throw new BusinessRuleException("Sự kiện không thuộc dòng họ hiện tại");
        }

        if (eventSummary.status() == EventStatus.Closed) {
            throw new BusinessRuleException("Không thể tiếp nhận đóng góp cho sự kiện đã khóa sổ");
        }

        // 2. Verify Payer Member existence and tenancy
        MemberSummaryDto payerMember = genealogyQueryService.findMemberSummary(request.payerMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên nộp tiền không tồn tại với ID: " + request.payerMemberId()));

        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(payerMember.clanId())) {
            throw new BusinessRuleException("Thành viên nộp tiền không thuộc dòng họ hiện tại");
        }

        // 3. Resolve Receiver Account ID (Thủ quỹ / BTC tiếp nhận)
        String receiverAccountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new BusinessRuleException("Không xác định được tài khoản người tiếp nhận đóng góp"));

        // 4. Generate unique IDs (#69: sinhPhieuThuDienTu & contributionId)
        String contributionId = generateUniqueContributionId();
        String receiptNumber = generateUniqueReceiptNumber();

        // 5. Handle Allocations (#70: phanBoGiaTri)
        List<ContributionMemberDetail> memberDetails = new ArrayList<>();
        List<MemberAllocationResponse> allocationResponses = new ArrayList<>();

        if (request.allocations() == null || request.allocations().isEmpty()) {
            // Case 1: Single payer takes 100% contribution allocation
            memberDetails.add(ContributionMemberDetail.builder()
                    .contributionId(contributionId)
                    .memberId(request.payerMemberId())
                    .recordedAmount(request.amount())
                    .build());

            allocationResponses.add(new MemberAllocationResponse(
                    request.payerMemberId(),
                    payerMember.fullName(),
                    request.amount()
            ));
        } else {
            // Case 2: Multiple descendants joint contribution
            // 5.1 Pre-validate structure: duplicates, positive amounts, and total sum
            Set<String> seenMembers = new HashSet<>();
            BigDecimal totalAllocated = BigDecimal.ZERO;

            for (MemberAllocationRequest alloc : request.allocations()) {
                if (!seenMembers.add(alloc.memberId())) {
                    throw new BusinessRuleException("Trùng lặp thành viên trong danh sách phân bổ: " + alloc.memberId());
                }
                if (alloc.amount() == null || alloc.amount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessRuleException("Số tiền phân bổ cho thành viên " + alloc.memberId() + " phải lớn hơn 0");
                }
                totalAllocated = totalAllocated.add(alloc.amount());
            }

            if (totalAllocated.compareTo(request.amount()) != 0) {
                throw new BusinessRuleException(String.format(
                        "Tổng số tiền phân bổ (%s) không khớp với tổng tiền đóng góp (%s)",
                        totalAllocated, request.amount()
                ));
            }

            // 5.2 Validate genealogy member existence and clan tenancy
            for (MemberAllocationRequest alloc : request.allocations()) {
                MemberSummaryDto mSummary = genealogyQueryService.findMemberSummary(alloc.memberId())
                        .orElseThrow(() -> new ResourceNotFoundException("Thành viên phân bổ không tồn tại với ID: " + alloc.memberId()));

                if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(mSummary.clanId())) {
                    throw new BusinessRuleException("Thành viên phân bổ " + alloc.memberId() + " không thuộc dòng họ hiện tại");
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

        // 6. Persist Parent Contribution
        Contribution contribution = Contribution.builder()
                .contributionId(contributionId)
                .eventId(request.eventId())
                .payerMemberId(request.payerMemberId())
                .receiverAccountId(receiverAccountId)
                .contributionDate(now)
                .contributionType(ContributionType.Cash)
                .totalMonetaryValue(request.amount())
                .build();
        contributionRepository.save(contribution);

        // 7. Persist Cash Contribution Child Record
        CashContribution cashContribution = CashContribution.builder()
                .contributionId(contributionId)
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .receiptNumber(receiptNumber)
                .build();
        cashContributionRepository.save(cashContribution);

        // 8. Persist All Member Allocation Details
        contributionMemberDetailRepository.saveAll(memberDetails);

        log.info("Successfully recorded cash contribution {} with receipt {}", contributionId, receiptNumber);

        // 9. Return Electronic Receipt
        String amountInWords = MoneyToWordsConverter.toVietnameseWords(request.amount());

        return new ElectronicReceiptResponse(
                receiptNumber,
                contributionId,
                request.eventId(),
                eventSummary.eventName(),
                request.payerMemberId(),
                payerMember.fullName(),
                receiverAccountId,
                request.amount(),
                amountInWords,
                request.paymentMethod(),
                now,
                allocationResponses
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ElectronicReceiptResponse getReceiptByReceiptNumber(String receiptNumber) {
        CashContribution cashContribution = cashContributionRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biên lai với số: " + receiptNumber));

        Contribution contribution = contributionRepository.findById(cashContribution.getContributionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin đóng góp cho biên lai: " + receiptNumber));

        EventSummaryDto eventSummary = eventQueryService.findEventSummary(contribution.getEventId())
                .orElse(new EventSummaryDto(contribution.getEventId(), null, "Sự kiện", null, null));

        MemberSummaryDto payerMember = genealogyQueryService.findMemberSummary(contribution.getPayerMemberId())
                .orElse(new MemberSummaryDto(contribution.getPayerMemberId(), "Thành viên", null, null));

        List<ContributionMemberDetail> details = contributionMemberDetailRepository.findAllByContributionId(contribution.getContributionId());
        List<MemberAllocationResponse> allocations = details.stream().map(d -> {
            String name = genealogyQueryService.findMemberSummary(d.getMemberId())
                    .map(MemberSummaryDto::fullName)
                    .orElse("Thành viên");
            return new MemberAllocationResponse(d.getMemberId(), name, d.getRecordedAmount());
        }).toList();

        return new ElectronicReceiptResponse(
                cashContribution.getReceiptNumber(),
                contribution.getContributionId(),
                contribution.getEventId(),
                eventSummary.eventName(),
                contribution.getPayerMemberId(),
                payerMember.fullName(),
                contribution.getReceiverAccountId(),
                cashContribution.getAmount(),
                MoneyToWordsConverter.toVietnameseWords(cashContribution.getAmount()),
                cashContribution.getPaymentMethod(),
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
        throw new IllegalStateException("Không thể tạo mã đóng góp duy nhất sau 5 lần thử");
    }

    private String generateUniqueReceiptNumber() {
        for (int i = 0; i < 5; i++) {
            String candidate = receiptNumberGenerator.generate();
            if (!cashContributionRepository.existsByReceiptNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không thể tạo số biên lai phiếu thu duy nhất sau 5 lần thử");
    }
}
