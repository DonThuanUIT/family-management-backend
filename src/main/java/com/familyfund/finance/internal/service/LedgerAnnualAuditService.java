package com.familyfund.finance.internal.service;

import com.familyfund.finance.api.dto.LedgerAnnualAuditStatus;
import com.familyfund.finance.internal.dto.request.AnnualLedgerAuditRequest;
import com.familyfund.finance.internal.dto.response.LedgerAnnualAuditResponse;

import java.util.List;

/**
 * Service interface for executing annual common-fund ledger reconciliations and querying audit history.
 */
public interface LedgerAnnualAuditService {

    /**
     * Compares physical counted cash against the latest {@code immutable_ledgers.running_balance}:
     * <ul>
     *   <li>If matched: appends a zero-amount {@code OpeningBalanceCarryForward} entry sealed with SHA-256 and records {@code Matched}.</li>
     *   <li>If discrepant: auto-generates a US-15 compensating adjustment request and records {@code DiscrepancyDetected}.</li>
     * </ul>
     */
    LedgerAnnualAuditResponse performAnnualAudit(AnnualLedgerAuditRequest request);

    /**
     * Lists annual ledger audit records for the active clan tenant, optionally filtered by year and status.
     */
    List<LedgerAnnualAuditResponse> searchAnnualAudits(Integer auditYear, LedgerAnnualAuditStatus status);

    /**
     * Retrieves detailed annual audit information by {@code auditId} within the active clan tenant.
     */
    LedgerAnnualAuditResponse getAnnualAuditById(String auditId);
}
