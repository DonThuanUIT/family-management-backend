package com.familyfund.finance.internal.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload for executing an annual common-fund ledger audit and cash count reconciliation.
 */
public record AnnualLedgerAuditRequest(
        @NotNull(message = "Số tiền thực kiểm không được để trống")
        @DecimalMin(value = "0.00", inclusive = true, message = "Số tiền thực kiểm không được âm")
        @Digits(integer = 16, fraction = 2,
                message = "Số tiền thực kiểm không hợp lệ (tối đa 16 chữ số nguyên và 2 chữ số thập phân)")
        @JsonAlias({"actual_balance", "actualCashBalance", "countedBalance"})
        BigDecimal actualBalance,

        @Min(value = 2000, message = "Năm kiểm toán phải từ 2000 trở lên")
        @Max(value = 2100, message = "Năm kiểm toán không được vượt quá 2100")
        @JsonAlias({"audit_year", "fiscalYear", "year"})
        Integer auditYear,

        @Size(max = 20, message = "Mã giao dịch đối chiếu không được vượt quá 20 ký tự")
        @JsonAlias({"reference_transaction_id", "originalTransactionId"})
        String referenceTransactionId,

        @Size(max = 2000, message = "Ghi chú kiểm toán không được vượt quá 2000 ký tự")
        @JsonAlias({"audit_notes", "note", "reason"})
        String notes,

        @Size(max = 500, message = "Đường dẫn biên bản/chứng từ kiểm toán không được vượt quá 500 ký tự")
        @JsonAlias({"evidence_attachment_url", "evidenceUrl"})
        String evidenceAttachmentUrl
) {}
