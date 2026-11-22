package com.familyfund.finance.internal.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for Clan Head / Admin to approve (with password verification) or reject
 * a common fund disbursement proposal (US-60).
 */
public record ReviewCommonFundDisbursementRequest(
        @Size(max = 20, message = "Mã đề xuất chi Quỹ chung không được vượt quá 20 ký tự")
        @JsonAlias("request_id")
        String requestId,

        @NotNull(message = "Quyết định phê duyệt (approved) không được để trống")
        @JsonAlias("is_approved")
        Boolean approved,

        @Size(max = 200, message = "Mật khẩu xác nhận không được vượt quá 200 ký tự")
        @JsonAlias({"confirm_password", "password"})
        String confirmPassword,

        @Size(max = 2000, message = "Lý do từ chối không được vượt quá 2000 ký tự")
        @JsonAlias({"rejection_reason", "reason"})
        String rejectionReason,

        @Size(max = 2000, message = "Mô tả bút toán sổ cái không được vượt quá 2000 ký tự")
        @JsonAlias({"review_note", "ledger_description"})
        String description
) {}
