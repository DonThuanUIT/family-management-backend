package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.response.MemberContributionHistoryResponse;

/**
 * Service providing personal and household contribution histories and PDF receipts (US-49).
 */
public interface ContributionHistoryService {

    /**
     * Retrieves the contribution history for the currently logged-in member or their household/branch.
     *
     * @param includeFamily whether to include all contributions across the same household/branch
     * @return contribution history details
     */
    MemberContributionHistoryResponse getMyContributionHistory(boolean includeFamily);

    /**
     * Generates a downloadable electronic receipt in PDF format by receipt number.
     *
     * @param receiptNumber unique receipt number
     * @return PDF byte array
     */
    byte[] downloadReceiptPdf(String receiptNumber);
}
