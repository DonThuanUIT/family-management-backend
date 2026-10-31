package com.familyfund.contribution.internal.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateEventItemBenchmarkRequest(
        @NotBlank(message = "Item name is required")
        @Size(max = 100, message = "Item name cannot exceed 100 characters")
        String itemName,

        @NotBlank(message = "Unit is required")
        @Size(max = 20, message = "Unit cannot exceed 20 characters")
        String unit,

        @NotNull(message = "Unit price is required")
        @PositiveOrZero(message = "Unit price must be greater than or equal to 0")
        BigDecimal unitPrice,

        @Size(max = 255, message = "Note cannot exceed 255 characters")
        String note
) {}
