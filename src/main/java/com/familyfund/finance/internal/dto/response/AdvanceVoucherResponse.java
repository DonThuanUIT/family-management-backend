package com.familyfund.finance.internal.dto.response;

import com.familyfund.finance.api.dto.AdvanceVoucherStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Detailed view of an advance voucher (US-46).
 */
public record AdvanceVoucherResponse(
        String advanceVoucherId,
        String taskId,
        String taskName,
        String eventId,
        String eventName,
        String clanId,
        String recipientMemberId,
        String recipientName,
        BigDecimal advanceAmount,
        Instant receivedDate,
        Instant settlementDeadline,
        String commitmentNotes,
        String createdByAccountId,
        String creatorName,
        AdvanceVoucherStatus status,
        String treasurerAccountId,
        String treasurerName,
        Instant disbursedAt,
        String disbursementNotes,
        Instant overdueAt,
        Instant createdAt,
        Instant updatedAt
) {}
