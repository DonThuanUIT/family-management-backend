package com.familyfund.finance.internal.service;

import com.familyfund.finance.internal.dto.response.LedgerIntegrityReportResponse;

/**
 * Service interface for cryptographic SHA-256 hash-chain integrity verification of
 * {@code immutable_ledgers} (NFR-01 / US-61).
 */
public interface ImmutableLedgerIntegrityService {

    /**
     * Verifies every ledger entry of the active tenant clan chronologically from {@code GENESIS}.
     */
    LedgerIntegrityReportResponse verifyCurrentClanChain();
}
