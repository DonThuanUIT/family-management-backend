package com.familyfund.contribution.internal.dto;

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
        @NotBlank(message = "Mã sự kiện không được để trống")
        String eventId,

        @NotBlank(message = "Mã thành viên nộp tiền không được để trống")
        String payerMemberId,

        @NotNull(message = "Số tiền đóng góp không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền đóng góp phải lớn hơn 0")
        BigDecimal amount,

        @NotNull(message = "Hình thức thanh toán không được để trống (Cash hoặc BankTransfer)")
        PaymentMethod paymentMethod,

        @Valid
        List<MemberAllocationRequest> allocations
) {
}
