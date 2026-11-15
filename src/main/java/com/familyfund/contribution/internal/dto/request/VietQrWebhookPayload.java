package com.familyfund.contribution.internal.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

/**
 * Inbound webhook payload for bank payment notifications (US-53 Task #186).
 * Compatible with payment gateways such as SePay, Casso, and Napas-standard bank webhooks.
 */
public record VietQrWebhookPayload(
        String gateway,
        String transactionDate,
        String accountNumber,
        String code,
        String content,
        String transferType,
        @JsonAlias({"transferAmount", "amount"})
        BigDecimal transferAmount,
        @JsonAlias({"referenceCode", "refCode", "transactionId"})
        String referenceCode
) {
}
