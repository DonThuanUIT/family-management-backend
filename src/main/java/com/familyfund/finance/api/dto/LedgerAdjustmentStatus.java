package com.familyfund.finance.api.dto;

/**
 * Approval workflow status of a ledger adjustment request in {@code ledger_adjustment_requests} (US-15).
 *
 * <ul>
 *   <li>{@link #Pending} - Proposed and awaiting review by Clan Head / Admin.</li>
 *   <li>{@link #Approved} - Approved and offset transaction recorded in immutable ledger.</li>
 *   <li>{@link #Rejected} - Rejected with reason.</li>
 * </ul>
 */
public enum LedgerAdjustmentStatus {
    Pending,
    Approved,
    Rejected
}
