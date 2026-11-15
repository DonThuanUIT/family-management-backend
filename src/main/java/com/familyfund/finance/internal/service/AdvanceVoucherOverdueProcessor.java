package com.familyfund.finance.internal.service;

import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventTaskSummaryDto;
import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.internal.dto.OverdueProcessingResult;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import com.familyfund.finance.internal.repository.AdvanceVoucherRepository;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Encapsulates the transactional state transition and simultaneous warning dispatch
 * for a SINGLE overdue advance voucher (US-48).
 *
 * <p>Isolated transaction on purpose: if a single voucher encounters transient failure (e.g., downstream
 * notification issue), only that unit rolls back while the overall scan pass continues uninterrupted
 * with remaining vouchers.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdvanceVoucherOverdueProcessor {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final AdvanceVoucherRepository advanceVoucherRepository;
    private final EventQueryService eventQueryService;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final GenealogyQueryService genealogyQueryService;
    private final NotificationService notificationService;

    /**
     * Evaluates, transitions status to {@link AdvanceVoucherStatus#QuaHan}, and sends simultaneous
     * warning notifications to both the recipient and the event organizers (BTC).
     *
     * @param voucherId     ID of the advance voucher
     * @param referenceTime timestamp representing 'now'
     * @return processing outcome with counts of warnings dispatched
     */
    @Transactional(rollbackFor = Exception.class)
    public OverdueProcessingResult processOverdueVoucher(String voucherId, Instant referenceTime) {
        if (voucherId == null || voucherId.isBlank()) {
            return new OverdueProcessingResult(voucherId, false, 0);
        }

        Optional<AdvanceVoucher> opt = advanceVoucherRepository.findById(voucherId.trim());
        if (opt.isEmpty()) {
            log.warn("Advance voucher {} not found during overdue processing", voucherId);
            return new OverdueProcessingResult(voucherId, false, 0);
        }

        AdvanceVoucher voucher = opt.get();

        // Idempotency: only transition if status is still ChuaTatToan and deadline has actually passed
        if (voucher.getStatus() != AdvanceVoucherStatus.ChuaTatToan) {
            log.debug("Voucher {} skipped from overdue transition; current status: {}", voucherId, voucher.getStatus());
            return new OverdueProcessingResult(voucherId, false, 0);
        }

        if (voucher.getSettlementDeadline() != null && !voucher.getSettlementDeadline().isBefore(referenceTime)) {
            log.debug("Voucher {} skipped; deadline {} is not yet past reference time {}",
                    voucherId, voucher.getSettlementDeadline(), referenceTime);
            return new OverdueProcessingResult(voucherId, false, 0);
        }

        // 1. Transition status to QuaHan and mark overdue timestamp
        voucher.setStatus(AdvanceVoucherStatus.QuaHan);
        voucher.setOverdueAt(referenceTime);
        voucher.setUpdatedAt(referenceTime);
        advanceVoucherRepository.save(voucher);
        log.info("Advance voucher {} transitioned to QuaHan (settlement deadline was {})",
                voucherId, voucher.getSettlementDeadline());

        // 2. Resolve metadata for notifications
        EventTaskSummaryDto taskSummary = eventQueryService.findTaskSummary(voucher.getTaskId()).orElse(null);
        String taskName = taskSummary != null ? taskSummary.taskName() : "Hạng mục " + voucher.getTaskId();
        String eventId = taskSummary != null ? taskSummary.eventId() : null;
        String clanId = voucher.getClanId() != null
                ? voucher.getClanId()
                : (taskSummary != null ? taskSummary.clanId() : null);

        String recipientName = genealogyQueryService.findMemberSummary(voucher.getRecipientMemberId())
                .map(MemberSummaryDto::fullName)
                .orElse(voucher.getRecipientMemberId());

        String formattedAmount = formatCurrency(voucher.getAdvanceAmount());
        String formattedDeadline = voucher.getSettlementDeadline() != null
                ? DATE_TIME_FORMATTER.format(voucher.getSettlementDeadline())
                : "N/A";

        int notificationsSent = 0;

        // 3. Send warning notification to the recipient (người nhận tiền)
        try {
            Optional<AccountSummaryDto> recipientAccount =
                    identityRoleCommandService.findAccountByMemberId(voucher.getRecipientMemberId());
            if (recipientAccount.isPresent()) {
                String recipientAccountId = recipientAccount.get().accountId();
                String title = "Cảnh báo: Quá hạn quyết toán tạm ứng";
                String content = String.format(
                        "Phiếu tạm ứng %s (số tiền %s đ) cho công việc '%s' đã quá hạn nộp chứng từ quyết toán (%s). Vui lòng khẩn trương hoàn tất quyết toán.",
                        voucher.getAdvanceVoucherId(), formattedAmount, taskName, formattedDeadline);

                notificationService.notify(NotificationCommand.builder()
                        .clanId(clanId)
                        .recipientAccountId(recipientAccountId)
                        .category(NotificationCategory.Finance)
                        .title(title)
                        .content(content)
                        .referenceType("ADVANCE_VOUCHER")
                        .referenceId(voucher.getAdvanceVoucherId())
                        .build());
                notificationsSent++;
                log.debug("Sent overdue alert to recipient account {} for voucher {}", recipientAccountId, voucherId);
            } else {
                log.info("Recipient member {} has no active account; skipping direct recipient inbox notification",
                        voucher.getRecipientMemberId());
            }
        } catch (Exception ex) {
            log.warn("Failed to dispatch overdue notification to recipient of voucher {}: {}", voucherId, ex.getMessage());
        }

        // 4. Send warning notification to the Event Organizers (Ban tổ chức / BTC)
        Set<String> organizerAccountIds = new LinkedHashSet<>();
        if (eventId != null && !eventId.isBlank()) {
            Set<String> btcMemberIds = eventQueryService.findOrganizerMemberIds(eventId);
            if (!btcMemberIds.isEmpty()) {
                Map<String, AccountSummaryDto> accounts = identityRoleCommandService.findAccountsByMemberIds(btcMemberIds);
                accounts.values().stream()
                        .map(AccountSummaryDto::accountId)
                        .filter(Objects::nonNull)
                        .forEach(organizerAccountIds::add);
            }
        }

        // Also include creator of the voucher and treasurer who disbursed it if set
        if (voucher.getCreatedByAccountId() != null && !voucher.getCreatedByAccountId().isBlank()) {
            organizerAccountIds.add(voucher.getCreatedByAccountId().trim());
        }
        if (voucher.getTreasurerAccountId() != null && !voucher.getTreasurerAccountId().isBlank()) {
            organizerAccountIds.add(voucher.getTreasurerAccountId().trim());
        }

        String btcTitle = "Cảnh báo: Phiếu tạm ứng quá hạn quyết toán";
        String btcContent = String.format(
                "Phiếu tạm ứng %s của người nhận %s (số tiền %s đ) thuộc công việc '%s' đã quá hạn nộp chứng từ quyết toán (%s).",
                voucher.getAdvanceVoucherId(), recipientName, formattedAmount, taskName, formattedDeadline);

        for (String organizerAccountId : organizerAccountIds) {
            try {
                notificationService.notify(NotificationCommand.builder()
                        .clanId(clanId)
                        .recipientAccountId(organizerAccountId)
                        .category(NotificationCategory.Finance)
                        .title(btcTitle)
                        .content(btcContent)
                        .referenceType("ADVANCE_VOUCHER")
                        .referenceId(voucher.getAdvanceVoucherId())
                        .build());
                notificationsSent++;
                log.debug("Sent overdue alert to organizer account {} for voucher {}", organizerAccountId, voucherId);
            } catch (Exception ex) {
                log.warn("Failed to dispatch overdue notification to organizer account {} for voucher {}: {}",
                        organizerAccountId, voucherId, ex.getMessage());
            }
        }

        return new OverdueProcessingResult(voucherId, true, notificationsSent);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(amount);
    }
}
