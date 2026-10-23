package com.familyfund.identity.internal.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for account login (US-10).
 */
public record LoginRequest(
        @NotBlank(message = "Số điện thoại hoặc email không được để trống")
        String loginIdentifier,

        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {
}
