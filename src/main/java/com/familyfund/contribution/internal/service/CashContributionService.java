package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.RecordCashContributionRequest;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;

import java.util.List;

/**
 * Internal service interface for handling cash contributions and electronic receipts.
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
}
