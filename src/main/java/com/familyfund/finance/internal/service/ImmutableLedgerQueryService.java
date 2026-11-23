package com.familyfund.finance.internal.service;

import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.response.LedgerHistoryItemResponse;
import com.familyfund.finance.internal.dto.response.LedgerHistoryPageResponse;

import java.util.List;

/**
 * Read-only query service for searching and inspecting the common fund immutable ledger
 * ({@code immutable_ledgers}) with bidirectional adjustment links and cash-flow summary (US-62).
 */
public interface ImmutableLedgerQueryService {

    LedgerHistoryPageResponse searchLedgerHistory(
            String from,
            String to,
            List<LedgerTransactionType> transactionTypes,
            String eventId,
            String keyword,
            Boolean onlyWithAdjustments,
            String sortDirection,
            int page,
            int size
    );

    LedgerHistoryItemResponse getTransactionDetail(String transactionId);
}
