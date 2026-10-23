package com.familyfund.identity.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.identity.internal.dto.request.LoginRequest;
import com.familyfund.identity.internal.dto.request.RefreshTokenRequest;
import com.familyfund.identity.internal.dto.request.RegisterCreatorRequest;
import com.familyfund.identity.internal.dto.response.AuthTokenResponse;
import com.familyfund.identity.internal.dto.response.CreatorRegistrationResponse;
import com.familyfund.identity.internal.service.AuthService;
import com.familyfund.identity.internal.service.CreatorRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication & Registration REST Controller (US-01, US-10).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CreatorRegistrationService creatorRegistrationService;
    private final AuthService authService;

    /**
     * Registers a new clan creator account and bootstraps a brand new clan and initial family tree (US-01).
     */
    @PostMapping("/register-creator")
    public ResponseEntity<ApiResponse<CreatorRegistrationResponse>> registerCreator(
            @Valid @RequestBody RegisterCreatorRequest request
    ) {
        CreatorRegistrationResponse response = creatorRegistrationService.registerCreator(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đăng ký người khởi tạo và tạo dòng họ mới thành công"));
    }

    /**
     * Authenticates user credentials and issues JWT access & refresh tokens (US-10).
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthTokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đăng nhập thành công"));
    }

    /**
     * Renews an expired access token using a valid refresh token (US-10).
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Làm mới token thành công"));
    }
}
