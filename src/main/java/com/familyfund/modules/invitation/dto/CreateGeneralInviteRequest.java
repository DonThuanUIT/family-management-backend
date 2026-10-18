package com.familyfund.modules.invitation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request payload for creating a general clan invitation (US-16).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGeneralInviteRequest {

    @Size(max = 20, message = "Mã nhánh không được vượt quá 20 ký tự")
    private String targetBranchId;

    @Min(value = 1, message = "Số lượt sử dụng tối đa phải từ 1 trở lên")
    private Integer maxUses;

    @Future(message = "Thời điểm hết hạn phải ở tương lai")
    private Instant expiresAt;

    @Min(value = 1, message = "Số ngày hiệu lực phải từ 1 trở lên")
    private Integer validityDays;
}

