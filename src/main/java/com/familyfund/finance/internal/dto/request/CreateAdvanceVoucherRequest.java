package com.familyfund.finance.internal.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request payload for creating an advance voucher for an event task (US-46).
 */
public record CreateAdvanceVoucherRequest(
        @Size(max = 20, message = "Mã phiếu tạm ứng tối đa 20 ký tự")
        String advanceVoucherId,

        @NotBlank(message = "Mã công việc (taskId) không được để trống")
        @Size(max = 20, message = "Mã công việc tối đa 20 ký tự")
        String taskId,

        @NotBlank(message = "Mã người nhận (recipientMemberId) không được để trống")
        @Size(max = 20, message = "Mã người nhận tối đa 20 ký tự")
        String recipientMemberId,

        @NotNull(message = "Số tiền tạm ứng không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền tạm ứng phải lớn hơn 0")
        BigDecimal advanceAmount,

        @NotNull(message = "Ngày nhận tiền tạm ứng không được để trống")
        Instant receivedDate,

        @NotNull(message = "Hạn nộp chứng từ quyết toán không được để trống")
        Instant settlementDeadline,

        @Size(max = 2000, message = "Nội dung cam kết tối đa 2000 ký tự")
        String commitmentNotes
) {}
