package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.request.GenerateVietQrRequest;
import com.familyfund.contribution.internal.dto.request.RecordCashContributionRequest;
import com.familyfund.contribution.internal.dto.request.VietQrWebhookPayload;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.VietQrResponse;
import com.familyfund.contribution.internal.dto.response.WebhookPaymentResultResponse;
import com.familyfund.contribution.internal.entity.CashContribution;
import com.familyfund.contribution.internal.entity.PaymentWebhook;
import com.familyfund.contribution.internal.repository.CashContributionRepository;
import com.familyfund.contribution.internal.repository.PaymentWebhookRepository;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.utils.BankBinDirectory;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.ClanBankingInfoDto;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VietQrServiceImpl implements VietQrService {

    private static final Pattern TRANSFER_CONTENT_PATTERN =
            Pattern.compile("(?i)FFUND\\s+(\\S+)\\s+(\\S+)");

    @Value("${app.vietqr.bank-bin:970436}")
    private String bankBin;

    @Value("${app.vietqr.account-number:1023456789}")
    private String accountNumber;

    @Value("${app.vietqr.account-name:QUY DONG HO GIA TOC}")
    private String accountName;

    @Value("${app.vietqr.template:compact2}")
    private String template;

    private final CashContributionService cashContributionService;
    private final CashContributionRepository cashContributionRepository;
    private final PaymentWebhookRepository paymentWebhookRepository;
    private final EventQueryService eventQueryService;
    private final GenealogyQueryService genealogyQueryService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public VietQrResponse generateVietQr(GenerateVietQrRequest request) {
        final String targetMemberId = (request.memberId() != null && !request.memberId().isBlank())
                ? request.memberId()
                : currentUserProvider.currentMemberId()
                .orElseThrow(() -> new BusinessRuleException("Member ID is required to generate personalized VietQR"));

        // 1. Verify Event
        EventSummaryDto eventSummary = eventQueryService.findEventSummary(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + request.eventId()));

        if (eventSummary.status() == EventStatus.Closed) {
            throw new BusinessRuleException("Cannot generate VietQR contribution for a closed event");
        }

        // 2. Verify Member
        MemberSummaryDto member = genealogyQueryService.findMemberSummary(targetMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + targetMemberId));

        // 3. Build standardized transfer description: FFUND {eventId} {memberId}
        String transferContent = String.format("FFUND %s %s", request.eventId().trim(), targetMemberId.trim());

        // 4. Resolve Clan beneficiary banking info (multi-tenancy) with fallback to application config
        String targetBankBin = bankBin;
        String targetAccountNumber = accountNumber;
        String targetAccountName = accountName;
        String targetTemplate = template;

        Optional<ClanBankingInfoDto> clanBankingOpt = genealogyQueryService.getClanBankingInfo(eventSummary.clanId());
        if (clanBankingOpt.isPresent()) {
            ClanBankingInfoDto clanBanking = clanBankingOpt.get();
            if (clanBanking.bankBin() != null && !clanBanking.bankBin().isBlank()) {
                targetBankBin = clanBanking.bankBin().trim();
            }
            if (clanBanking.bankAccountNumber() != null && !clanBanking.bankAccountNumber().isBlank()) {
                targetAccountNumber = clanBanking.bankAccountNumber().trim();
            }
            if (clanBanking.bankAccountName() != null && !clanBanking.bankAccountName().isBlank()) {
                targetAccountName = clanBanking.bankAccountName().trim();
            }
            if (clanBanking.qrTemplate() != null && !clanBanking.qrTemplate().isBlank()) {
                targetTemplate = clanBanking.qrTemplate().trim();
            }
        }

        // 5. Construct QuickLink VietQR image URL
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String encodedAccountName = URLEncoder.encode(targetAccountName, StandardCharsets.UTF_8);
        long amountValue = request.amount().longValue();

        String qrCodeUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-%s.png?amount=%d&addInfo=%s&accountName=%s",
                targetBankBin, targetAccountNumber, targetTemplate, amountValue, encodedContent, encodedAccountName
        );

        // 6. Construct EMVCo Payload representation
        String qrPayload = String.format("vietqr://transfer?bin=%s&acc=%s&amt=%d&desc=%s",
                targetBankBin, targetAccountNumber, amountValue, encodedContent);

        return new VietQrResponse(
                qrCodeUrl,
                qrPayload,
                targetBankBin,
                BankBinDirectory.lookupBankName(targetBankBin),
                targetAccountNumber,
                targetAccountName,
                request.amount(),
                transferContent,
                eventSummary.eventId(),
                eventSummary.eventName(),
                member.memberId(),
                member.fullName()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebhookPaymentResultResponse processPaymentWebhook(VietQrWebhookPayload payload) {
        log.info("Processing inbound payment webhook: refCode={}, amount={}, content={}",
                payload.referenceCode(), payload.transferAmount(), payload.content());

        String refCode = payload.referenceCode();
        if (refCode == null || refCode.isBlank()) {
            refCode = payload.code() != null ? payload.code() : "TXN_" + UUID.randomUUID().toString().substring(0, 8);
        }

        // 1. Idempotency Check
        Optional<PaymentWebhook> existingWebhook = paymentWebhookRepository.findByReferenceCode(refCode);
        if (existingWebhook.isPresent()) {
            PaymentWebhook existing = existingWebhook.get();
            log.info("Duplicate webhook received for refCode: {}, returning existing contribution {}",
                    refCode, existing.getContributionId());
            ElectronicReceiptResponse existingReceipt = null;
            if (existing.getContributionId() != null) {
                Optional<CashContribution> cashOpt = cashContributionRepository.findById(existing.getContributionId());
                if (cashOpt.isPresent() && cashOpt.get().getReceiptNumber() != null) {
                    try {
                        existingReceipt = cashContributionService.getReceiptByReceiptNumber(cashOpt.get().getReceiptNumber());
                    } catch (Exception ignored) {
                    }
                }
            }
            return new WebhookPaymentResultResponse(
                    true,
                    "Webhook transaction already processed (idempotent)",
                    refCode,
                    existing.getContributionId(),
                    existingReceipt != null ? existingReceipt.receiptNumber() : null,
                    existingReceipt
            );
        }

        BigDecimal amount = payload.transferAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            recordWebhookLog(refCode, payload, "Failed", null);
            return new WebhookPaymentResultResponse(false, "Invalid transaction amount", refCode, null, null);
        }

        // 2. Parse transfer content for standard format: FFUND {eventId} {memberId}
        String content = payload.content();
        if (content == null || content.isBlank()) {
            recordWebhookLog(refCode, payload, "Ignored", null);
            return new WebhookPaymentResultResponse(false, "Transfer content is empty", refCode, null, null);
        }

        Matcher matcher = TRANSFER_CONTENT_PATTERN.matcher(content);
        if (!matcher.find()) {
            recordWebhookLog(refCode, payload, "Ignored", null);
            return new WebhookPaymentResultResponse(
                    false,
                    "Transfer content does not match standard pattern 'FFUND {eventId} {memberId}'",
                    refCode,
                    null,
                    null
            );
        }

        String eventId = matcher.group(1);
        String memberId = matcher.group(2);

        // 3. Verify event and member
        EventSummaryDto eventSummary = eventQueryService.findEventSummary(eventId).orElse(null);
        if (eventSummary == null) {
            recordWebhookLog(refCode, payload, "Failed", null);
            return new WebhookPaymentResultResponse(false, "Event not found: " + eventId, refCode, null, null);
        }

        MemberSummaryDto member = genealogyQueryService.findMemberSummary(memberId).orElse(null);
        if (member == null) {
            recordWebhookLog(refCode, payload, "Failed", null);
            return new WebhookPaymentResultResponse(false, "Member not found: " + memberId, refCode, null, null);
        }

        // 4. Automatically record cash contribution as BankTransfer
        String originalClanId = TenantContext.getClanId();
        try {
            TenantContext.setClanId(eventSummary.clanId());

            RecordCashContributionRequest contributionRequest = new RecordCashContributionRequest(
                    eventId,
                    memberId,
                    amount,
                    PaymentMethod.BankTransfer,
                    payload.allocations() != null && !payload.allocations().isEmpty() ? payload.allocations() : null
            );

            ElectronicReceiptResponse receipt = cashContributionService.recordCashContribution(contributionRequest);

            // 5. Update bank reconciliation details on cash contribution
            Optional<CashContribution> cashOpt = cashContributionRepository.findById(receipt.contributionId());
            if (cashOpt.isPresent()) {
                CashContribution cash = cashOpt.get();
                cash.setBankTransactionCode(refCode);
                cash.setIsReconciled(true);
                cash.setReconciledAt(Instant.now());
                cash.setReconciledBy("VietQR-Webhook");
                cashContributionRepository.save(cash);
            }

            // 6. Record processed webhook log
            recordWebhookLog(refCode, payload, "Processed", receipt.contributionId());

            return new WebhookPaymentResultResponse(
                    true,
                    "Payment verified, cash contribution recorded and electronic receipt issued successfully",
                    refCode,
                    receipt.contributionId(),
                    receipt.receiptNumber(),
                    receipt
            );
        } finally {
            if (originalClanId != null) {
                TenantContext.setClanId(originalClanId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void recordWebhookLog(String refCode, VietQrWebhookPayload payload, String status, String contributionId) {
        PaymentWebhook webhook = PaymentWebhook.builder()
                .webhookId("WH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .referenceCode(refCode)
                .gateway(payload.gateway() != null ? payload.gateway() : "VietQR")
                .amount(payload.transferAmount() != null ? payload.transferAmount() : BigDecimal.ZERO)
                .transferContent(payload.content())
                .status(status)
                .contributionId(contributionId)
                .build();

        paymentWebhookRepository.save(webhook);
    }

    private String lookupBankName(String bin) {
        return BankBinDirectory.lookupBankName(bin);
    }
}
