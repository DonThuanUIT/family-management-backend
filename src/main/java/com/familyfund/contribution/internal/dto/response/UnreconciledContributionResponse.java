package com.familyfund.contribution.internal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representation of an unreconciled bank-transfer contribution awaiting treasurer audit (US-50).
 */
public record UnreconciledContributionResponse(
        String contributionId,
        String receiptNumber,
        String eventId,
        String eventName,
        String payerMemberId,
        String payerMemberName,
        BigDecimal amount,
        String amountInWords,
        Instant contributionDate,
        String bankTransactionCode,
        String transferProofUrl,
        Boolean isReconciled
) {
}
