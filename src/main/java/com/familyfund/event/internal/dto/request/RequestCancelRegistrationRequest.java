package com.familyfund.event.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for requesting cancellation of an existing registration (US-06).
 */
public record RequestCancelRegistrationRequest(
        @NotBlank(message = "Lý do yêu cầu hủy đăng ký không được để trống")
        @Size(max = 255, message = "Lý do yêu cầu hủy không được vượt quá 255 ký tự")
        String cancellationReason
) {
}
