package com.familyfund.finance.internal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Current clan common fund balance snapshot derived from the latest {@code immutable_ledgers} entry (US-60).
 */
public record CommonFundBalanceResponse(
        String clanId,
        BigDecimal availableBalance,
        BigDecimal pendingDisbursementAmount,
        String latestTransactionId,
        Instant latestTransactionAt
) {}
