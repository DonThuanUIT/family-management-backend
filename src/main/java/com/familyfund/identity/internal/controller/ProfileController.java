package com.familyfund.identity.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.identity.internal.dto.request.ChangePasswordRequest;
import com.familyfund.identity.internal.dto.request.UpdateContactProfileRequest;
import com.familyfund.identity.internal.dto.response.UserProfileResponse;
import com.familyfund.identity.internal.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for User Profile and Self-Service Password Management (US-22).
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Retrieves the profile details of the currently authenticated member.
     */
    @GetMapping({"/api/v1/profile/me", "/api/profile/me"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        UserProfileResponse profile = profileService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.success(profile, "Lấy thông tin hồ sơ cá nhân thành công"));
    }

    /**
     * Updates the contact details (phone, email, address) of the currently authenticated member.
     * Demographic genealogy fields (full_name, date_of_birth, generation) are strictly immutable here.
     */
    @PutMapping({"/api/v1/profile/me", "/api/profile/me"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateContactProfile(
            @Valid @RequestBody UpdateContactProfileRequest request
    ) {
        UserProfileResponse updatedProfile = profileService.updateContactProfile(request);
        return ResponseEntity.ok(ApiResponse.success(updatedProfile, "Cập nhật thông tin liên lạc thành công"));
    }

    /**
     * Changes the password of the currently authenticated member.
     */
    @PostMapping({"/api/v1/profile/change-password", "/api/profile/change-password"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        profileService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Đổi mật khẩu thành công"));
    }
}
