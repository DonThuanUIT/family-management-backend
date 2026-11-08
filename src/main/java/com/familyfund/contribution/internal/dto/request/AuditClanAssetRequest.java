package com.familyfund.contribution.internal.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload to conduct an inventory audit of a clan asset (US-52 Task #183).
 */
public record AuditClanAssetRequest(
        @NotNull(message = "Actual quantity is required")
        @Min(value = 0, message = "Actual quantity must be greater than or equal to 0")
        Integer actualQuantity,

        @NotBlank(message = "Condition status is required (e.g. Good, Damaged, NeedsRepair, Lost, Liquidated)")
        @Size(max = 50, message = "Condition status cannot exceed 50 characters")
        String conditionStatus,

        @Size(max = 255, message = "Storage location cannot exceed 255 characters")
        String storageLocation,

        @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
        String notes
) {
}
