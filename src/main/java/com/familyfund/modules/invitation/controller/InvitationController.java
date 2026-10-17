package com.familyfund.modules.invitation.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.modules.invitation.dto.CreateGeneralInviteRequest;
import com.familyfund.modules.invitation.dto.InviteCodeResponse;
import com.familyfund.modules.invitation.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * REST Controller for Managing Clan Invitations (US-16).
 */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    /**
     * Creates a general clan invitation code and shareable link (US-16).
     * Restricted to Clan Head (ROLE_CLAN_HEAD) or Admin (ROLE_ADMIN).
     */
    @PostMapping("/general")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> createGeneralInvite(
            @Valid @RequestBody CreateGeneralInviteRequest request,
            Authentication authentication
    ) {
        String currentAccountId = resolveAccountId(authentication);
        InviteCodeResponse response = invitationService.createGeneralClanInvite(request, currentAccountId);

        URI location = URI.create("/api/v1/invitations/" + response.getInviteCode());
        return ResponseEntity.created(location)
                .body(ApiResponse.success(response, "Tạo mã mời chung gia phả thành công"));
    }

    /**
     * Lists all active general clan invitations.
     * Restricted to Clan Head (ROLE_CLAN_HEAD) or Admin (ROLE_ADMIN).
     */
    @GetMapping("/general")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<InviteCodeResponse>>> getActiveGeneralInvites() {
        List<InviteCodeResponse> invites = invitationService.getActiveGeneralInvites();
        return ResponseEntity.ok(ApiResponse.success(invites, "Lấy danh sách mã mời thành công"));
    }

    /**
     * Retrieves invitation code details for preview (Public access for US-17 onboarding).
     */
    @GetMapping("/{inviteCode}")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> getInviteByCode(@PathVariable String inviteCode) {
        InviteCodeResponse invite = invitationService.getInviteByCode(inviteCode);
        return ResponseEntity.ok(ApiResponse.success(invite, "Lấy thông tin mã mời thành công"));
    }

    /**
     * Deactivates an invitation code early.
     * Restricted to Clan Head (ROLE_CLAN_HEAD) or Admin (ROLE_ADMIN).
     */
    @PatchMapping("/{inviteCode}/deactivate")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateInvite(
            @PathVariable String inviteCode,
            Authentication authentication
    ) {
        String currentAccountId = resolveAccountId(authentication);
        invitationService.deactivateInviteCode(inviteCode, currentAccountId);
        return ResponseEntity.ok(ApiResponse.success(null, "Vô hiệu hóa mã mời thành công"));
    }

    private String resolveAccountId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        return "ACC_ANONYMOUS";
    }
}

