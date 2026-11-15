package com.familyfund.event.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.event.internal.dto.request.UpdateRegistrationRequest;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho phép đại diện hộ gia đình trực tiếp cập nhật hoặc hủy đăng ký
 * khi sự kiện đang ở trạng thái RegistrationOpen (US-41).
 */
@RestController
@RequestMapping({"/api/v1/registrations", "/api/registrations"})
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> updateRegistration(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateRegistrationRequest request
    ) {
        RegistrationResponse response = registrationService.updateRegistration(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật thông tin đăng ký thành công"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> cancelRegistration(
            @PathVariable("id") String id
    ) {
        RegistrationResponse response = registrationService.cancelRegistration(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Hủy đăng ký tham dự thành công"));
    }
}
