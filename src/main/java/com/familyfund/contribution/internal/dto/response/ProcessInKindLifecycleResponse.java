package com.familyfund.contribution.internal.dto.response;

import com.familyfund.contribution.api.dto.InKindProcessingStatus;

/**
 * Response payload after processing in-kind contribution lifecycle (Task #73).
 */
public record ProcessInKindLifecycleResponse(
        String contributionId,
        String receiptNumber,
        String itemName,
        InKindProcessingStatus processingStatus,
        String note,
        ClanAssetResponse asset
) {
}
