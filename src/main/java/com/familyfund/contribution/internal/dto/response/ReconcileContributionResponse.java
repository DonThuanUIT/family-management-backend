package com.familyfund.contribution.internal.dto.response;

import java.time.Instant;

/**
 * Result of confirming bank transfer reconciliation (US-50).
 */
public record ReconcileContributionResponse(
        String contributionId,
        String receiptNumber,
        String bankTransactionCode,
        String transferProofUrl,
        Boolean isReconciled,
        Instant reconciledAt,
        String reconciledBy,
        String message
) {
}
