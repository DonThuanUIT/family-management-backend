package com.familyfund.finance.internal.dto.response;

/**
 * Result of reviewing a common fund disbursement request, carrying the append-only ledger entry when approved (US-60).
 */
public record ReviewCommonFundDisbursementResponse(
        CommonFundDisbursementResponse disbursementRequest,
        ImmutableLedgerEntryResponse ledgerEntry
) {}
