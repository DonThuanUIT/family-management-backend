package com.familyfund.finance.internal.dto.response;

import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.api.dto.LedgerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Enriched ledger transaction history item with bidirectional links to original and
 * adjustment transactions, human-readable names, and SHA-256 hash chain fields (US-62).
 */
public record LedgerHistoryItemResponse(
        String transactionId,
        String clanId,
        String eventId,
        String eventName,
        String adjustmentRequestId,
        String adjustmentReason,
        String evidenceAttachmentUrl,
        String disbursementRequestId,
        String disbursementTitle,
        CommonFundExpenseCategory disbursementCategory,
        String originalTransactionId,
        LedgerTransactionType transactionType,
        BigDecimal amount,
        BigDecimal runningBalance,
        String description,
        String createdByAccountId,
        String createdByName,
        String approvedByAccountId,
        String approvedByName,
        String previousHash,
        String recordHash,
        Instant createdAt,
        boolean isAdjustment,
        boolean hasAdjustments,
        int adjustmentCount,
        BigDecimal netAdjustedAmount,
        int pendingAdjustmentRequestCount,
        LinkedLedgerTransactionDto originalTransaction,
        List<LinkedLedgerTransactionDto> adjustmentTransactions
) {}
