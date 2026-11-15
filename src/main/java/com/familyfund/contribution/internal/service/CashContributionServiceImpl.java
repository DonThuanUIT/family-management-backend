package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.request.MemberAllocationRequest;
import com.familyfund.contribution.internal.dto.request.ReconcileContributionRequest;
import com.familyfund.contribution.internal.dto.request.RecordCashContributionRequest;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.MemberAllocationResponse;
import com.familyfund.contribution.internal.dto.response.ReconcileContributionResponse;
import com.familyfund.contribution.internal.dto.response.UnreconciledContributionResponse;
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
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
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
    private final IdentityRoleCommandService identityRoleCommandService;
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ElectronicReceiptResponse recordCashContribution(RecordCashContributionRequest request) {
        log.info("Processing cash contribution for event {} by payer {}", request.eventId(), request.payerMemberId());

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
                    throw new BusinessRuleException("Duplicate member in allocation list: " + alloc.memberId());
                }
                if (alloc.amount() == null || alloc.amount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessRuleException("Allocated amount for member " + alloc.memberId() + " must be greater than 0");
                }
                totalAllocated = totalAllocated.add(alloc.amount());
            }

            if (totalAllocated.compareTo(request.amount()) != 0) {
                throw new BusinessRuleException(String.format(
                        "Total allocated amount (%s) does not match total contribution amount (%s)",
                        totalAllocated, request.amount()
                ));
            }

            // 5.2 Validate genealogy member existence and clan tenancy
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

        // 8.5 US-39: put the electronic receipt into the payer's Finance inbox (no-op when the member has no account).
        notifyPayerOfRecordedContribution(currentClanId, eventSummary, request, contributionId, receiptNumber, now);

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

    /**
     * US-39: writes the electronic receipt into the payer's personal inbox under the {@code Finance} category.
     *
     * <p>Best effort on purpose: a notification problem must never fail the receipt that was just persisted, and a
     * payer member without a linked account (placeholder or unregistered) simply has no inbox to write to.</p>
     */
    private void notifyPayerOfRecordedContribution(String clanId,
                                                   EventSummaryDto eventSummary,
                                                   RecordCashContributionRequest request,
                                                   String contributionId,
                                                   String receiptNumber,
                                                   Instant recordedAt) {
        String targetClanId = (clanId != null && !clanId.isBlank()) ? clanId.trim() : eventSummary.clanId();
        if (targetClanId == null || targetClanId.isBlank()) {
            return;
        }
        try {
            identityRoleCommandService.findAccountByMemberId(request.payerMemberId()).ifPresent(account ->
                    notificationService.notify(NotificationCommand.builder()
                            .clanId(targetClanId)
                            .recipientAccountId(account.accountId())
                            .category(NotificationCategory.Finance)
                            .title("Đã ghi nhận đóng góp " + request.amount().toPlainString() + "đ")
                            .content("Biên nhận " + receiptNumber + " cho sự kiện '" + eventSummary.eventName()
                                    + "' đã được ghi nhận lúc " + recordedAt + ".")
                            .referenceType("CONTRIBUTION")
                            .referenceId(contributionId)
                            .build()));
        } catch (RuntimeException ex) {
            log.warn("US-39: cannot create the Finance notification for contribution {}: {}", contributionId, ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ElectronicReceiptResponse getReceiptByReceiptNumber(String receiptNumber) {
        CashContribution cashContribution = cashContributionRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with number: " + receiptNumber));

        Contribution contribution = contributionRepository.findById(cashContribution.getContributionId())
                .orElseThrow(() -> new ResourceNotFoundException("Contribution information not found for receipt: " + receiptNumber));

        String currentClanId = TenantContext.getClanId();
        EventSummaryDto eventSummary = eventQueryService.findEventSummary(contribution.getEventId())
                .orElse(new EventSummaryDto(contribution.getEventId(), null, "Event", null, null));

        if (currentClanId != null && !currentClanId.isBlank() && eventSummary.clanId() != null
                && !currentClanId.equals(eventSummary.clanId())) {
            throw new ResourceNotFoundException("Receipt not found with number: " + receiptNumber);
        }

        MemberSummaryDto payerMember = genealogyQueryService.findMemberSummary(contribution.getPayerMemberId())
                .orElse(new MemberSummaryDto(contribution.getPayerMemberId(), "Member", null, null));

        List<ContributionMemberDetail> details = contributionMemberDetailRepository.findAllByContributionId(contribution.getContributionId());
        List<MemberAllocationResponse> allocations = details.stream().map(d -> {
            String name = genealogyQueryService.findMemberSummary(d.getMemberId())
                    .map(MemberSummaryDto::fullName)
                    .orElse("Member");
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

    @Override
    @Transactional(readOnly = true)
    public List<ElectronicReceiptResponse> getCashContributionsByEvent(String eventId) {
        List<Contribution> contributions = contributionRepository.findByEventId(eventId);
        if (contributions == null || contributions.isEmpty()) {
            return Collections.emptyList();
        }

        EventSummaryDto eventSummary = eventQueryService.findEventSummary(eventId)
                .orElse(new EventSummaryDto(eventId, null, "Đại Lễ Giỗ Tổ", null, null));

        List<ElectronicReceiptResponse> responses = new ArrayList<>();
        for (Contribution contribution : contributions) {
            if (contribution.getContributionType() != ContributionType.Cash) {
                continue;
            }

            CashContribution cashContribution = cashContributionRepository.findById(contribution.getContributionId())
                    .orElse(null);
            if (cashContribution == null) {
                continue;
            }

            MemberSummaryDto payerMember = genealogyQueryService.findMemberSummary(contribution.getPayerMemberId())
                    .orElse(new MemberSummaryDto(contribution.getPayerMemberId(), "Thành viên", null, null));

            List<ContributionMemberDetail> details = contributionMemberDetailRepository.findAllByContributionId(contribution.getContributionId());
            List<MemberAllocationResponse> allocations = details.stream().map(d -> {
                String name = genealogyQueryService.findMemberSummary(d.getMemberId())
                        .map(MemberSummaryDto::fullName)
                        .orElse("Thành viên");
                return new MemberAllocationResponse(d.getMemberId(), name, d.getRecordedAmount());
            }).toList();

            responses.add(new ElectronicReceiptResponse(
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
            ));
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnreconciledContributionResponse> getUnreconciledContributions(String eventId) {
        String currentClanId = TenantContext.getClanId();
        if (eventId != null && !eventId.isBlank()) {
            EventSummaryDto eventSummary = eventQueryService.findEventSummary(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
            if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(eventSummary.clanId())) {
                throw new BusinessRuleException("Event does not belong to the current clan");
            }
            validateTreasuryPermission(eventId);
        } else {
            if (!currentUserProvider.hasAuthority(SystemRoles.ADMIN) && !currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
                throw new AccessDeniedException("Access denied: Viewing clan-wide unreconciled bank transfers requires Clan Head or Admin role");
            }
        }

        List<Object[]> rawList = cashContributionRepository.findUnreconciledBankTransfers(eventId);
        Map<String, String> eventNameCache = new HashMap<>();
        Map<String, String> memberNameCache = new HashMap<>();
        List<UnreconciledContributionResponse> result = new ArrayList<>();

        for (Object[] pair : rawList) {
            Contribution c = (Contribution) pair[0];
            CashContribution cc = (CashContribution) pair[1];

            String eventName = eventNameCache.computeIfAbsent(c.getEventId(), id ->
                    eventQueryService.findEventSummary(id).map(EventSummaryDto::eventName).orElse("Event " + id));

            String payerName = memberNameCache.computeIfAbsent(c.getPayerMemberId(), id ->
                    genealogyQueryService.findMemberSummary(id).map(MemberSummaryDto::fullName).orElse("Member " + id));

            result.add(new UnreconciledContributionResponse(
                    c.getContributionId(),
                    cc.getReceiptNumber(),
                    c.getEventId(),
                    eventName,
                    c.getPayerMemberId(),
                    payerName,
                    cc.getAmount(),
                    MoneyToWordsConverter.toVietnameseWords(cc.getAmount()),
                    c.getContributionDate(),
                    cc.getBankTransactionCode(),
                    cc.getTransferProofUrl(),
                    Boolean.TRUE.equals(cc.getIsReconciled())
            ));
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconcileContributionResponse reconcileContribution(
            String contributionId,
            ReconcileContributionRequest request) {
        log.info("Processing bank transfer reconciliation for contribution {}", contributionId);

        Contribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found with ID: " + contributionId));

        CashContribution cashContribution = cashContributionRepository.findById(contributionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash contribution record not found with ID: " + contributionId));

        String currentClanId = TenantContext.getClanId();
        EventSummaryDto eventSummary = eventQueryService.findEventSummary(contribution.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + contribution.getEventId()));

        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(eventSummary.clanId())) {
            throw new BusinessRuleException("Contribution does not belong to the current clan");
        }

        validateTreasuryPermission(contribution.getEventId());

        if (cashContribution.getPaymentMethod() != PaymentMethod.BankTransfer) {
            throw new BusinessRuleException("Only contributions made via BankTransfer can be reconciled with bank statements");
        }

        if (Boolean.TRUE.equals(cashContribution.getIsReconciled())) {
            throw new BusinessRuleException("Contribution is already reconciled");
        }

        Optional<CashContribution> existingWithCode = cashContributionRepository.findByBankTransactionCode(request.bankTransactionCode());
        if (existingWithCode.isPresent() && !existingWithCode.get().getContributionId().equals(contributionId)) {
            throw new BusinessRuleException("Bank transaction code '" + request.bankTransactionCode() + "' is already matched with contribution " + existingWithCode.get().getContributionId());
        }

        Instant now = Instant.now();
        String auditorAccountId = currentUserProvider.currentAccountId().orElse("System");

        cashContribution.setBankTransactionCode(request.bankTransactionCode());
        cashContribution.setTransferProofUrl(request.transferProofUrl());
        cashContribution.setIsReconciled(true);
        cashContribution.setReconciledAt(now);
        cashContribution.setReconciledBy(auditorAccountId);

        cashContributionRepository.save(cashContribution);

        return new ReconcileContributionResponse(
                contributionId,
                cashContribution.getReceiptNumber(),
                cashContribution.getBankTransactionCode(),
                cashContribution.getTransferProofUrl(),
                true,
                now,
                auditorAccountId,
                "Bank transfer contribution successfully reconciled with bank statement"
        );
    }

    private void validateTreasuryPermission(String eventId) {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)
                || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return;
        }

        String memberId = currentUserProvider.currentMemberId().orElse(null);
        if (memberId != null && eventId != null && !eventId.isBlank()) {
            Set<EventRole> roles = eventQueryService.getMemberRolesInEvent(eventId, memberId);
            if (roles.contains(EventRole.EventTreasurer) || roles.contains(EventRole.HeadOfOrganizer)) {
                return;
            }
        }

        throw new AccessDeniedException("Access denied: You do not have treasury permission to manage event contributions or reconcile finances");
    }

    private String generateUniqueContributionId() {
        for (int i = 0; i < 5; i++) {
            String candidate = contributionIdGenerator.generate();
            if (!contributionRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique contribution ID after 5 attempts");
    }

    private String generateUniqueReceiptNumber() {
        for (int i = 0; i < 5; i++) {
            String candidate = receiptNumberGenerator.generate();
            if (!cashContributionRepository.existsByReceiptNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique receipt number after 5 attempts");
    }
}
