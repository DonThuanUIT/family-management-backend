package com.familyfund.contribution.internal.dto.request;

import com.familyfund.contribution.api.dto.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload to record a cash or bank transfer contribution and issue an electronic receipt.
 */
public record RecordCashContributionRequest(
        @NotBlank(message = "Event ID cannot be blank")
        String eventId,

        @NotBlank(message = "Payer member ID cannot be blank")
        String payerMemberId,

        @NotNull(message = "Contribution amount cannot be null")
        @DecimalMin(value = "0.01", message = "Contribution amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Payment method cannot be null (Cash or BankTransfer)")
        PaymentMethod paymentMethod,

        @Valid
        List<MemberAllocationRequest> allocations
) {
}
