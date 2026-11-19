package com.familyfund.finance.internal.service;

import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;

import java.util.List;

/**
 * Service interface for proposing and querying immutable ledger adjustment requests (US-15).
 */
public interface LedgerAdjustmentService {

    /**
     * Creates a new compensating adjustment proposal in {@code ledger_adjustment_requests}
     * linked to an existing {@code immutable_ledgers} entry.
     */
    LedgerAdjustmentRequestResponse proposeAdjustment(ProposeLedgerAdjustmentRequest request);

    /**
     * Retrieves detailed information of a ledger adjustment request by ID.
     */
    LedgerAdjustmentRequestResponse getAdjustmentRequestById(String requestId);

    /**
     * Lists ledger adjustment requests scoped to the caller's clan with optional filters.
     */
    List<LedgerAdjustmentRequestResponse> searchAdjustmentRequests(
            String originalTransactionId,
            LedgerAdjustmentStatus status
    );
}
