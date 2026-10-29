package com.familyfund.contribution.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request payload for individual member allocation in a joint contribution.
 */
public record MemberAllocationRequest(
        @NotBlank(message = "Mã thành viên phân bổ không được để trống")
        String memberId,

        @NotNull(message = "Số tiền phân bổ không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền phân bổ phải lớn hơn 0")
        BigDecimal amount
) {
}
