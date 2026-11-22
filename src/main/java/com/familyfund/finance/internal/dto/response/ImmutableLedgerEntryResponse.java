package com.familyfund.finance.internal.dto.response;

import com.familyfund.finance.api.dto.LedgerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response representation of an append-only entry in {@code immutable_ledgers} (NFR-01 / US-59).
 */
public record ImmutableLedgerEntryResponse(
        String transactionId,
        String clanId,
        String eventId,
        String adjustmentRequestId,
        String originalTransactionId,
        LedgerTransactionType transactionType,
        BigDecimal amount,
        BigDecimal runningBalance,
        String description,
        String createdByAccountId,
        String approvedByAccountId,
        String recordHash,
        Instant createdAt
) {}
