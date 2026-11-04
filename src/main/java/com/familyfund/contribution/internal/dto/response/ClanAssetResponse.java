package com.familyfund.contribution.internal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO representing an accumulated clan asset.
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
        Instant createdAt
) {
}
