package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.ReconcileContributionRequest;
import com.familyfund.contribution.internal.dto.request.RecordCashContributionRequest;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.ReconcileContributionResponse;
import com.familyfund.contribution.internal.dto.response.UnreconciledContributionResponse;

import java.util.List;

/**
 * Internal service interface for handling cash contributions, electronic receipts, and bank transfer reconciliation.
 */
public interface CashContributionService {

    /**
     * Records a cash/bank-transfer contribution, applies allocations, and generates an electronic receipt.
     *
     * @param request input payload
     * @return generated electronic receipt
     */
    ElectronicReceiptResponse recordCashContribution(RecordCashContributionRequest request);

    /**
     * Retrieves an existing electronic receipt by receipt number.
     *
     * @param receiptNumber unique receipt number (soBienLai)
     * @return electronic receipt details
     */
    ElectronicReceiptResponse getReceiptByReceiptNumber(String receiptNumber);

    /**
     * Retrieves all cash electronic receipts for a specific event.
     *
     * @param eventId target event ID
     * @return list of electronic receipts
     */
    List<ElectronicReceiptResponse> getCashContributionsByEvent(String eventId);

    /**
     * Lists all unreconciled bank-transfer contributions for an event or all events (US-50).
     *
     * @param eventId optional event ID to filter
     * @return list of unreconciled contributions
     */
    List<UnreconciledContributionResponse> getUnreconciledContributions(String eventId);

    /**
     * Reconciles a bank-transfer contribution by matching with bank transaction code and proof (US-50).
     *
     * @param contributionId target contribution ID
     * @param request reconciliation payload
     * @return reconciliation confirmation
     */
    ReconcileContributionResponse reconcileContribution(String contributionId, ReconcileContributionRequest request);
}
