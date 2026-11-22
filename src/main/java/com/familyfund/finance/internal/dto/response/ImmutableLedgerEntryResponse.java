package com.familyfund.finance.internal.dto.response;

import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.entity.ImmutableLedger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response representation of an append-only entry in {@code immutable_ledgers} (NFR-01 / US-59 / US-60).
 */
public record ImmutableLedgerEntryResponse(
        String transactionId,
        String clanId,
        String eventId,
        String adjustmentRequestId,
        String disbursementRequestId,
        String originalTransactionId,
        LedgerTransactionType transactionType,
        BigDecimal amount,
        BigDecimal runningBalance,
        String description,
        String createdByAccountId,
        String approvedByAccountId,
        String recordHash,
        Instant createdAt
) {

    /**
     * Maps an {@link ImmutableLedger} entity to its API response projection.
     */
    public static ImmutableLedgerEntryResponse fromEntity(ImmutableLedger entry) {
        return new ImmutableLedgerEntryResponse(
                entry.getTransactionId(),
                entry.getClanId(),
                entry.getEventId(),
                entry.getAdjustmentRequestId(),
                entry.getDisbursementRequestId(),
                entry.getOriginalTransactionId(),
                entry.getTransactionType(),
                entry.getAmount(),
                entry.getRunningBalance(),
                entry.getDescription(),
                entry.getCreatedByAccountId(),
                entry.getApprovedByAccountId(),
                entry.getRecordHash(),
                entry.getCreatedAt()
        );
    }
}
