package com.familyfund.finance.internal.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload for proposing a compensating ledger adjustment on an immutable ledger entry (US-15).
 */
public record ProposeLedgerAdjustmentRequest(
        @NotBlank(message = "Mã giao dịch sổ cái gốc không được để trống")
        @Size(max = 20, message = "Mã giao dịch sổ cái gốc không được vượt quá 20 ký tự")
        @JsonAlias("original_transaction_id")
        String originalTransactionId,

        @NotNull(message = "Số tiền bù trừ đề xuất không được để trống")
        @Digits(integer = 16, fraction = 2, message = "Số tiền bù trừ đề xuất không hợp lệ")
        @JsonAlias({"proposed_adjustment_amount", "adjustmentAmount", "amount"})
        BigDecimal proposedAdjustmentAmount,

        @NotBlank(message = "Lý do giải trình không được để trống")
        @Size(max = 2000, message = "Lý do giải trình không được vượt quá 2000 ký tự")
        @JsonAlias({"adjustment_reason", "reason"})
        String adjustmentReason,

        @Size(max = 500, message = "Đường dẫn bằng chứng đính kèm không được vượt quá 500 ký tự")
        @JsonAlias({"evidence_attachment_url", "evidenceUrl"})
        String evidenceAttachmentUrl
) {}
