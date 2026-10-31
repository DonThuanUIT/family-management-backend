package com.familyfund.contribution.internal.dto.request;

import com.familyfund.contribution.api.dto.ConversionMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload to record an in-kind contribution and issue an electronic receipt (US-10, Task #72).
 */
public record RecordInKindContributionRequest(
        @NotBlank(message = "Event ID cannot be blank")
        @Size(max = 20, message = "Event ID cannot exceed 20 characters")
        String eventId,

        @NotBlank(message = "Payer member ID cannot be blank")
        @Size(max = 20, message = "Payer member ID cannot exceed 20 characters")
        String payerMemberId,

        @NotBlank(message = "Item name cannot be blank")
        @Size(max = 100, message = "Item name cannot exceed 100 characters")
        String itemName,

        @NotNull(message = "Quantity cannot be null")
        @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
        BigDecimal quantity,

        @NotBlank(message = "Unit cannot be blank")
        @Size(max = 20, message = "Unit cannot exceed 20 characters")
        String unit,

        BigDecimal estimatedValue,

        @NotNull(message = "Conversion method cannot be null (BtcBenchmark, Invoice, Agreement)")
        ConversionMethod conversionMethod,

        @NotNull(message = "Converted monetary value cannot be null")
        @DecimalMin(value = "0.00", message = "Converted monetary value must be greater than or equal to 0")
        BigDecimal convertedMonetaryValue,

        String benchmarkId,

        String invoiceReference,

        @Valid
        List<MemberAllocationRequest> allocations
) {
}
