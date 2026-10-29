package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.RecordCashContributionRequest;

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
}
