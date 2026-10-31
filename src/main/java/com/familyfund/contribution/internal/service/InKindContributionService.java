package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import com.familyfund.contribution.internal.dto.request.ProcessInKindLifecycleRequest;
import com.familyfund.contribution.internal.dto.request.RecordInKindContributionRequest;
import com.familyfund.contribution.internal.dto.response.InKindContributionDetailResponse;
import com.familyfund.contribution.internal.dto.response.InKindElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.ProcessInKindLifecycleResponse;

import java.util.List;

public interface InKindContributionService {

    InKindElectronicReceiptResponse recordInKindContribution(RecordInKindContributionRequest request);

    InKindElectronicReceiptResponse getReceiptByReceiptNumber(String receiptNumber);

    InKindContributionDetailResponse getInKindContributionById(String contributionId);

    ProcessInKindLifecycleResponse processLifecycle(String contributionId, ProcessInKindLifecycleRequest request);

    List<InKindContributionDetailResponse> getInKindContributionsByEvent(String eventId, InKindProcessingStatus status);
}
