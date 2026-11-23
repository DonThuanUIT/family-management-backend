package com.familyfund.finance.internal.repository;

import com.familyfund.finance.api.dto.LedgerTransactionType;
import java.time.Instant;
import java.util.List;

public record LedgerSearchCriteria(
        String clanId,
        Instant fromInstant,
        Instant toInstant,
        List<LedgerTransactionType> transactionTypes,
        String eventId,
        String keyword,
        Boolean onlyWithAdjustments
) {}
