package com.familyfund.finance.internal.dto.response;

import java.util.List;

/**
 * Paginated response containing common fund ledger transactions and flow summary (US-62).
 */
public record LedgerHistoryPageResponse(
        String clanId,
        List<LedgerHistoryItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        LedgerFlowSummaryResponse summary
) {}
