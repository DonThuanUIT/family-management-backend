package com.familyfund.contribution.internal.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request payload for individual member allocation in a joint contribution.
 */
public record MemberAllocationRequest(
        @NotBlank(message = "Allocated member ID cannot be blank")
        String memberId,

        @NotNull(message = "Allocated amount cannot be null")
        @DecimalMin(value = "0.01", message = "Allocated amount must be greater than 0")
        BigDecimal amount
) {
}
