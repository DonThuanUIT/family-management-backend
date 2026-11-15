package com.familyfund.contribution.internal.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload to generate a dynamic VietQR payment code for clan contributions (US-53 Task #186).
 */
public record GenerateVietQrRequest(
        @NotBlank(message = "Event ID is required")
        String eventId,

        String memberId,

        @NotNull(message = "Contribution amount is required")
        @DecimalMin(value = "1000.00", message = "Contribution amount must be at least 1,000 VND")
        BigDecimal amount,

        @Size(max = 255, message = "Note cannot exceed 255 characters")
        String note
) {
}
