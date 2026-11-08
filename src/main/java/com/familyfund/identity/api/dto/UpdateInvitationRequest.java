package com.familyfund.identity.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request payload for updating an existing invitation code (US-16 follow-up).
 *
 * <p>Every field is optional; the service applies only the fields that are present. The same payload is reused by
 * the regenerate endpoint, where {@code expiresAt} / {@code validityDays} / {@code maxUses} override the values
 * inherited from the replaced code.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInvitationRequest {

    /**
     * Re-activate ({@code true}) or revoke ({@code false}) the code. Revoking keeps the row for audit purposes.
     */
    private Boolean isActive;

    @Future(message = "Thời điểm hết hạn phải ở tương lai")
    private Instant expiresAt;

    @Min(value = 1, message = "Số ngày hiệu lực phải từ 1 trở lên")
    private Integer validityDays;

    @Min(value = 1, message = "Số lượt sử dụng tối đa phải từ 1 trở lên")
    private Integer maxUses;
}