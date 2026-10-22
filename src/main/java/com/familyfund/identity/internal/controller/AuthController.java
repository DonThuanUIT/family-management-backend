package com.familyfund.identity.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.identity.internal.dto.request.RegisterCreatorRequest;
import com.familyfund.identity.internal.dto.response.CreatorRegistrationResponse;
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
 * Authentication & Registration REST Controller (US-01).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CreatorRegistrationService creatorRegistrationService;

    /**
     * Registers a new clan creator account and bootstraps a brand new clan and initial family tree.
     */
    @PostMapping("/register-creator")
    public ResponseEntity<ApiResponse<CreatorRegistrationResponse>> registerCreator(
            @Valid @RequestBody RegisterCreatorRequest request
    ) {
        CreatorRegistrationResponse response = creatorRegistrationService.registerCreator(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đăng ký người khởi tạo và tạo dòng họ mới thành công"));
    }
}
