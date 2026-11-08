package com.familyfund.contribution.internal.dto.response;

import java.util.List;

/**
 * Audit pre-check response alerting about pending in-kind contributions prior to event closure (US-51 / US-57).
 */
public record EventPendingInKindCheckResponse(
        String eventId,
        String eventName,
        boolean hasPendingItems,
        int pendingCount,
        String warningMessage,
        List<InKindContributionDetailResponse> pendingItems
) {
}
