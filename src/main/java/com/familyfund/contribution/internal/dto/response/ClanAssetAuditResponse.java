package com.familyfund.contribution.internal.dto.response;

import java.time.Instant;

/**
 * Historical audit log item for a clan asset (US-52).
 */
public record ClanAssetAuditResponse(
        String auditId,
        String assetId,
        Instant auditDate,
        Integer previousQuantity,
        Integer actualQuantity,
        String previousCondition,
        String conditionStatus,
        String storageLocation,
        String auditorAccountId,
        String notes,
        Instant createdAt
) {
}
