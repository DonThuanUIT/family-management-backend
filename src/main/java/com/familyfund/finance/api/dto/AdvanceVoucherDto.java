package com.familyfund.finance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public DTO representing an advance voucher for cross-module consumption (US-46).
 */
public record AdvanceVoucherDto(
        String advanceVoucherId,
        String taskId,
        String eventId,
        String clanId,
        String recipientMemberId,
        BigDecimal advanceAmount,
        Instant receivedDate,
        Instant settlementDeadline,
        String commitmentNotes,
        String createdByAccountId,
        AdvanceVoucherStatus status,
        String treasurerAccountId,
        Instant disbursedAt,
        String disbursementNotes,
        Instant overdueAt,
        Instant createdAt
) {}
