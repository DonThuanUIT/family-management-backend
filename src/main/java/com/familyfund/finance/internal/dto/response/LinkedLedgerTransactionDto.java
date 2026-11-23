package com.familyfund.finance.internal.dto.response;

import com.familyfund.finance.api.dto.LedgerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Compact linked transaction snapshot used for bidirectional linking between an original
 * ledger transaction and its compensating {@code AdjustmentTransaction} entries (US-62).
 */
public record LinkedLedgerTransactionDto(
        String transactionId,
        LedgerTransactionType transactionType,
        BigDecimal amount,
        BigDecimal runningBalance,
        String description,
        String adjustmentRequestId,
        String adjustmentReason,
        String evidenceAttachmentUrl,
        String createdByAccountId,
        String createdByName,
        String approvedByAccountId,
        String approvedByName,
        Instant createdAt
) {}
