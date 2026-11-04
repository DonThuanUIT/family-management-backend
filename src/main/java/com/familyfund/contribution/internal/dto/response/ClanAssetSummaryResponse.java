package com.familyfund.contribution.internal.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Paginated summary response of clan assets including total counts and assessed values.
 */
public record ClanAssetSummaryResponse(
        List<ClanAssetResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize,
        Long totalItemCount,
        BigDecimal totalAssessedValue
) {
}
