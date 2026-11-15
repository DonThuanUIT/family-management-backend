package com.familyfund.contribution.internal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO representing an accumulated clan asset with condition status and audit trail (US-52).
 */
public record ClanAssetResponse(
        String assetId,
        String assetName,
        Integer quantity,
        String unit,
        BigDecimal assessedValue,
        String storageLocation,
        String originContributionId,
        String clanId,
        String currentCondition,
        Instant lastAuditedAt,
        String lastAuditedBy,
        Instant createdAt
) {
    /**
     * Backward-compatible constructor for existing tests and consumers.
     */
    public ClanAssetResponse(
            String assetId,
            String assetName,
            Integer quantity,
            String unit,
            BigDecimal assessedValue,
            String storageLocation,
            String originContributionId,
            String clanId,
            Instant createdAt
    ) {
        this(assetId, assetName, quantity, unit, assessedValue, storageLocation, originContributionId, clanId, "Good", null, null, createdAt);
    }
}
