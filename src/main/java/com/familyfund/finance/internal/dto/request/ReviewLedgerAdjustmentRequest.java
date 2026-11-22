package com.familyfund.finance.internal.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for reviewing (approving or rejecting) a pending ledger adjustment proposal (US-59).
 */
public record ReviewLedgerAdjustmentRequest(
        @NotBlank(message = "Mã đề xuất điều chỉnh không được để trống")
        @Size(max = 20, message = "Mã đề xuất điều chỉnh không được vượt quá 20 ký tự")
        @JsonAlias("request_id")
        String requestId,

        @NotNull(message = "Quyết định phê duyệt (approved) không được để trống")
        @JsonAlias("is_approved")
        Boolean approved,

        @Size(max = 2000, message = "Lý do từ chối không được vượt quá 2000 ký tự")
        @JsonAlias({"rejection_reason", "reason"})
        String rejectionReason,

        @Size(max = 2000, message = "Mô tả bút toán không được vượt quá 2000 ký tự")
        @JsonAlias({"review_note", "ledger_description"})
        String description
) {}
