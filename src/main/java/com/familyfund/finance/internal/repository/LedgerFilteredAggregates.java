package com.familyfund.finance.internal.repository;

import java.math.BigDecimal;

public record LedgerFilteredAggregates(
        long totalCount,
        BigDecimal totalInflow,
        BigDecimal totalOutflow,
        BigDecimal netAmount
) {}
