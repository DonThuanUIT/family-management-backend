package com.familyfund.contribution.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload to reconcile a bank-transfer contribution with bank statement / proof (US-50).
 */
public record ReconcileContributionRequest(
        @NotBlank(message = "Bank transaction code is required")
        @Size(max = 100, message = "Bank transaction code cannot exceed 100 characters")
        String bankTransactionCode,

        @Size(max = 500, message = "Transfer proof URL cannot exceed 500 characters")
        String transferProofUrl,

        @Size(max = 255, message = "Note cannot exceed 255 characters")
        String note
) {
}
