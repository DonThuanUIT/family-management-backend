package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.RecordInKindContributionRequest;
import com.familyfund.contribution.internal.dto.response.InKindContributionDetailResponse;
import com.familyfund.contribution.internal.dto.response.InKindElectronicReceiptResponse;

public interface InKindContributionService {

    InKindElectronicReceiptResponse recordInKindContribution(RecordInKindContributionRequest request);

    InKindElectronicReceiptResponse getReceiptByReceiptNumber(String receiptNumber);

    InKindContributionDetailResponse getInKindContributionById(String contributionId);
}
