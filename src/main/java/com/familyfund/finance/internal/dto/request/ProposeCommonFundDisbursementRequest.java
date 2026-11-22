package com.familyfund.finance.internal.dto.request;

import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload for proposing a major expenditure disbursement from the clan common fund (US-60).
 */
public record ProposeCommonFundDisbursementRequest(
        @NotBlank(message = "Tiêu đề khoản chi không được để trống")
        @Size(max = 255, message = "Tiêu đề khoản chi không được vượt quá 255 ký tự")
        String title,

        @NotNull(message = "Danh mục chi Quỹ chung không được để trống")
        @JsonAlias("expense_category")
        CommonFundExpenseCategory category,

        @NotNull(message = "Số tiền chi không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền chi Quỹ chung phải lớn hơn 0")
        @Digits(integer = 16, fraction = 2, message = "Số tiền chi Quỹ chung không hợp lệ")
        BigDecimal amount,

        @Size(max = 255, message = "Tên người/đơn vị thụ hưởng không được vượt quá 255 ký tự")
        @JsonAlias("beneficiary_name")
        String beneficiaryName,

        @Size(max = 20, message = "Mã thành viên thụ hưởng không được vượt quá 20 ký tự")
        @JsonAlias("beneficiary_member_id")
        String beneficiaryMemberId,

        @NotBlank(message = "Mục đích / lý do chi Quỹ chung không được để trống")
        @Size(max = 2000, message = "Mục đích chi Quỹ chung không được vượt quá 2000 ký tự")
        @JsonAlias({"reason", "description"})
        String purpose,

        @Size(max = 500, message = "Đường dẫn chứng từ đính kèm không được vượt quá 500 ký tự")
        @JsonAlias({"attachment_url", "evidenceAttachmentUrl", "evidence_attachment_url"})
        String attachmentUrl
) {}
