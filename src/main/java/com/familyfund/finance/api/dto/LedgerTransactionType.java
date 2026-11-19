package com.familyfund.finance.api.dto;

/**
 * Transaction type for entries in {@code immutable_ledgers} (NFR-01 / US-15).
 *
 * <ul>
 *   <li>{@link #SurplusTransfer} - Transfer of surplus/deficit funds after an event settlement.</li>
 *   <li>{@link #AdjustmentTransaction} - Compensating adjustment transaction linked to an approved adjustment request.</li>
 * </ul>
 */
public enum LedgerTransactionType {
    SurplusTransfer,
    AdjustmentTransaction
}
