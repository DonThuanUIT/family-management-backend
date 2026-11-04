package com.familyfund.event.internal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for event organizers to review (approve or reject) a modification or cancellation request (US-06).
 */
public record ReviewRegistrationRequest(
        @NotNull(message = "Quyết định phê duyệt (approved) không được để trống")
        Boolean approved,

        @Size(max = 500, message = "Lý do từ chối không được vượt quá 500 ký tự")
        String rejectionReason
) {
}
