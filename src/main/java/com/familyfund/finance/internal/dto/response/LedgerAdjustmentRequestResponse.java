package com.familyfund.finance.internal.dto.response;

import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Detailed view of a ledger adjustment request linked to its original immutable ledger entry (US-15).
 */
public record LedgerAdjustmentRequestResponse(
        String requestId,
        String originalTransactionId,
        String clanId,
        String eventId,
        LedgerTransactionType originalTransactionType,
        BigDecimal originalTransactionAmount,
        BigDecimal proposedAdjustmentAmount,
        String adjustmentReason,
        String evidenceAttachmentUrl,
        String proposedByAccountId,
        LedgerAdjustmentStatus approvalStatus,
        String reviewedByAccountId,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt
) {}
