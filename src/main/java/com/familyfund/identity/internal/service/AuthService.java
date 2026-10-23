package com.familyfund.identity.internal.service;

import com.familyfund.identity.internal.dto.request.LoginRequest;
import com.familyfund.identity.internal.dto.request.RefreshTokenRequest;
import com.familyfund.identity.internal.dto.response.AuthTokenResponse;

/**
 * Authentication service contract for handling login and token renewal (US-10).
 */
public interface AuthService {

    /**
     * Authenticates user credentials (phone or email) and issues JWT access and refresh tokens.
     */
    AuthTokenResponse login(LoginRequest request);

    /**
     * Renews access token using a valid refresh token.
     */
    AuthTokenResponse refreshToken(RefreshTokenRequest request);
}
