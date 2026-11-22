package com.familyfund.finance.internal.dto.response;

/**
 * Result of reviewing a ledger adjustment proposal, including the new immutable ledger entry when approved (US-59).
 */
public record ReviewLedgerAdjustmentResponse(
        LedgerAdjustmentRequestResponse adjustmentRequest,
        ImmutableLedgerEntryResponse ledgerEntry
) {}
