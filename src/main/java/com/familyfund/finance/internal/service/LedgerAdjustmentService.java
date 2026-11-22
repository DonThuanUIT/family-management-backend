package com.familyfund.finance.internal.service;

import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.request.ReviewLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.dto.response.ReviewLedgerAdjustmentResponse;

import java.util.List;

/**
 * Service interface for proposing, reviewing and querying immutable ledger adjustment requests (US-15 / US-59).
 */
public interface LedgerAdjustmentService {

    /**
     * Creates a new compensating adjustment proposal in {@code ledger_adjustment_requests}
     * linked to an existing {@code immutable_ledgers} entry.
     */
    LedgerAdjustmentRequestResponse proposeAdjustment(ProposeLedgerAdjustmentRequest request);

    /**
     * Reviews (approves or rejects) a pending ledger adjustment proposal (US-59).
     * When approved, atomically appends an {@code AdjustmentTransaction} entry into
     * {@code immutable_ledgers} with updated {@code running_balance} and SHA-256 hash chain.
     */
    ReviewLedgerAdjustmentResponse reviewAdjustment(ReviewLedgerAdjustmentRequest request);

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
