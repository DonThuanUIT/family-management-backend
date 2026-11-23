package com.familyfund.finance.internal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fund balance and period cash-flow summary metrics for common fund ledger queries (US-62).
 */
public record LedgerFlowSummaryResponse(
        BigDecimal currentFundBalance,
        String latestTransactionId,
        Instant latestTransactionAt,
        BigDecimal openingBalanceBeforePeriod,
        BigDecimal closingBalanceAtPeriodEnd,
        long filteredTransactionCount,
        BigDecimal filteredTotalInflow,
        BigDecimal filteredTotalOutflow,
        BigDecimal filteredNetAmount
) {}
