package com.familyfund.finance.api.dto;

/**
 * Outcome status of an annual common-fund ledger reconciliation audit.
 *
 * <ul>
 *   <li>{@link #Matched} - Physical/actual counted cash matches ledger {@code running_balance}; opening balance carried forward.</li>
 *   <li>{@link #DiscrepancyDetected} - Actual cash differs from {@code running_balance}; a US-15 adjustment request was auto-generated.</li>
 * </ul>
 */
public enum LedgerAnnualAuditStatus {
    Matched,
    DiscrepancyDetected
}
