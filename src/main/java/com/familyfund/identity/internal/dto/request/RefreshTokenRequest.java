package com.familyfund.identity.internal.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for refreshing access token (US-10).
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken
) {
}
