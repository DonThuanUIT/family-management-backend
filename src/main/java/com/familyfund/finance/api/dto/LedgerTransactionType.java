package com.familyfund.finance.api.dto;

/**
 * Transaction type for entries in {@code immutable_ledgers} (NFR-01 / US-15 / US-60).
 *
 * <ul>
 *   <li>{@link #SurplusTransfer} - Transfer of surplus/deficit funds after an event settlement.</li>
 *   <li>{@link #AdjustmentTransaction} - Compensating adjustment transaction linked to an approved adjustment request.</li>
 *   <li>{@link #CommonFundDisbursement} - Approved expenditure disbursed from the clan common fund (US-60).</li>
 *   <li>{@link #OpeningBalanceCarryForward} - Zero-cashflow cryptographic rollover entry sealing the opening balance for the next fiscal year after a matched annual audit.</li>
 * </ul>
 */
public enum LedgerTransactionType {
    SurplusTransfer,
    AdjustmentTransaction,
    CommonFundDisbursement,
    OpeningBalanceCarryForward
}
