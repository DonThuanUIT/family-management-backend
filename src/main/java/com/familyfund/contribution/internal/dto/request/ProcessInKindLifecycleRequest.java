package com.familyfund.contribution.internal.dto.request;

import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload to process the lifecycle of an in-kind contribution (Task #73).
 */
public record ProcessInKindLifecycleRequest(
        @NotNull(message = "Target status is required (Consumed, Returned, AddedToCommonAssets)")
        InKindProcessingStatus targetStatus,

        @Size(max = 255, message = "Note cannot exceed 255 characters")
        String note,

        @Size(max = 100, message = "Asset name cannot exceed 100 characters")
        String assetName,

        Integer quantity,

        @Size(max = 20, message = "Unit cannot exceed 20 characters")
        String unit,

        BigDecimal assessedValue,

        @Size(max = 255, message = "Storage location cannot exceed 255 characters")
        String storageLocation
) {
}
