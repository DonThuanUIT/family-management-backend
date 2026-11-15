package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.genealogy.internal.dto.request.UpdateMemberPrivacySettingRequest;
import com.familyfund.genealogy.internal.dto.response.MemberPrivacySettingResponse;
import com.familyfund.genealogy.internal.service.MemberPrivacySettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for member personal privacy consent settings (US-25).
 */
@RestController
@RequestMapping("/api/v1/members/me/privacy-settings")
@RequiredArgsConstructor
public class MemberPrivacyController {

    private final MemberPrivacySettingService privacySettingService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MemberPrivacySettingResponse>> getMyPrivacySettings() {
        MemberPrivacySettingResponse response = privacySettingService.getMyPrivacySettings();
        return ResponseEntity.ok(ApiResponse.success(response, "Member privacy settings retrieved successfully"));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MemberPrivacySettingResponse>> updateMyPrivacySettings(
            @Valid @RequestBody UpdateMemberPrivacySettingRequest request) {
        MemberPrivacySettingResponse response = privacySettingService.updateMyPrivacySettings(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Member privacy settings updated successfully"));
    }
}
