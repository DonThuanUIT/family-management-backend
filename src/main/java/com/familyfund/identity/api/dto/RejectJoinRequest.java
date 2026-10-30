package com.familyfund.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for rejecting a branch join request (US-17).
 */
public record RejectJoinRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 500, message = "Lý do từ chối không được vượt quá 500 ký tự")
        String rejectionReason
) {}

