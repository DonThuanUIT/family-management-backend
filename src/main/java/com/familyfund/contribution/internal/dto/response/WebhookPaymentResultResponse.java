package com.familyfund.contribution.internal.dto.response;

/**
 * Result returned to the webhook caller after processing payment event (US-53).
 */
public record WebhookPaymentResultResponse(
        boolean success,
        String message,
        String referenceCode,
        String contributionId,
        String receiptNumber
) {
}
